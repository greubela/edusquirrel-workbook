package it.evadid.core.datastructures.storage.remote

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.remote.RemoteDataSynchronisation.*
import it.evadid.util.logging.derived.SyncLogger
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class RemoteDataSynchronisation[K, V, RW <: RemoteReaderWriter[K, V]](
    syncLogger: SyncLogger, readWriters: Set[RW]) {
  private given ExecutionContext = ExecutionContext.global
  private val syncLock = new Object
  private val innerState = State(RemoteCacheCollectionState(readWriters.map(_ -> RemoteCacheState.unknownState[K, V]).toMap))
  private val lastUpdateState = State(Map.empty[K, RemoteValue[K, V]])

  val updateReport: ObservableValue[Map[K, V]] = lastUpdateState.observable.deriveValue(_.map((key, entry) => key -> entry.value))
  val reports: ObservableValue[Map[K, CacheReportForKey[K, V, RW]]] = innerState.observable.deriveValue { state =>
    state.allKnownKeys.map { key =>
      key -> CacheReportForKey(key, state.cacheStateMap.flatMap((cache, status) => status.remoteValues.get(key).map(cache -> _)))
    }.toMap
  }

  private def updateCache(cache: RW)(change: RemoteCacheState[K, V] => RemoteCacheState[K, V]): Unit =
    innerState.update(_.updateCache(cache)(change))

  /** Order observations by when they were requested, not when a slow response arrives. */
  private def begin(cache: RW, fetching: Boolean): LocalDateTime = syncLock.synchronized {
    val status = innerState.now().cacheStateMap(cache)
    val last = List(status.timestampLastFetchRequested, status.timestampLastStoreRequested).max
    val now = LocalDateTime.now()
    val timestamp = if now.isAfter(last) then now else last.plusNanos(1)
    updateCache(cache)(state => if fetching then state.fetchRequested(timestamp) else state.storeRequested(timestamp))
    timestamp
  }

  private def attempt[A](run: => Future[A]): Future[A] = Try(run).fold(Future.failed, identity)

  def updateEverythingKnown(): Future[Unit] = ensureMaxAge(innerState.now().allKnownKeys, LocalDateTime.now(), true)

  def ensureMaxAge(ofKeys: Set[K], maximumAcceptableAge: LocalDateTime = LocalDateTime.now(), forceFlush: Boolean = false): Future[Unit] = {
    val outdated = innerState.now().getOutdatedKeysByCache(ofKeys, maximumAcceptableAge)
    // An empty request can still be the explicit flush of earlier buffered operations.
    if forceFlush then flush(true)
    Future.traverse(outdated.toList)((cache, keys) => executeFetch(cache, keys, forceFlush)).map(_ => ())
  }

  def requestStore(cache: RW, dataToStore: Map[K, V], maximumAcceptableAge: LocalDateTime = LocalDateTime.now(), forceFlush: Boolean = false): Future[Unit] = {
    require(readWriters(cache), "Cannot store to an unconfigured sync destination")
    if forceFlush then flush(true)
    val known = innerState.now().cacheStateMap(cache).remoteValues
    val changed = dataToStore.filter((key, value) => !known.get(key).exists(_.value == value))
    if changed.isEmpty then Future.unit else {
      val requested = begin(cache, fetching = false)
      val writing = attempt(cache.writer.writeAll(syncLogger, changed))
      Try(cache.writer.flush(syncLogger, forceFlush)).fold(Future.failed, _ => writing).map { _ =>
        syncLock.synchronized {
          updateCache(cache)(_.newInformationReceived(changed.map((key, value) => key -> RemoteValue[K, V](value, requested))))
        }
      }
    }
  }

  def requestStoreToAll(dataToStore: Map[K, V], maximumAcceptableAge: LocalDateTime = LocalDateTime.now(), forceFlush: Boolean = false): Future[Unit] =
    Future.traverse(readWriters)(cache => requestStore(cache, dataToStore, maximumAcceptableAge, forceFlush)).map(_ => ())

  private def executeFetch(cache: RW, keys: Set[K], forceFlush: Boolean): Future[Unit] = {
    if keys.isEmpty then Future.unit else {
      val requested = begin(cache, fetching = true)
      val reading = attempt(cache.reader.fetchByKeys(syncLogger, keys))
      Try(cache.reader.flush(syncLogger, forceFlush)).fold(Future.failed, _ => reading).map { response =>
        syncLock.synchronized {
          val incoming = response.asMap.filter((key, _) => keys(key)).map((key, value) => key -> RemoteValue[K, V](value.value, requested))
          updateCache(cache)(_.newInformationReceived(incoming))
          val confirmed = innerState.now().cacheStateMap(cache).remoteValues
          val accepted = incoming.filter((key, value) => confirmed.get(key).contains(value))
          if accepted.nonEmpty then lastUpdateState.set(accepted)
        }
      }.andThen { case Failure(error) => syncLogger.logExceptionWarn(s"Could not fetch ${keys.size} keys from sync destination", error) }
    }
  }

  def flush(forceFlush: Boolean = true): Unit = readWriters.foreach { cache =>
    cache.writer.flush(syncLogger, forceFlush)
    cache.reader.flush(syncLogger, forceFlush)
  }
}

object RemoteDataSynchronisation {
  case class RemoteCacheCollectionState[K, V, RW <: RemoteReaderWriter[K, V]](cacheStateMap: Map[RW, RemoteCacheState[K, V]]) {
    lazy val allKnownKeys: Set[K] = cacheStateMap.values.flatMap(_.remoteValues.keySet).toSet
    def updateCache(cache: RW)(change: RemoteCacheState[K, V] => RemoteCacheState[K, V]): RemoteCacheCollectionState[K, V, RW] =
      copy(cacheStateMap = cacheStateMap.updated(cache, change(cacheStateMap(cache))))

    def cachesWhereKeyIsOlder(key: K, maximumAcceptableAge: LocalDateTime): Set[RW] =
      cacheStateMap.collect { case (cache, state) if state.remoteValues.get(key).forall(_.timestampFetched.isBefore(maximumAcceptableAge)) => cache }.toSet

    def getOutdatedKeysByCache(keys: Set[K], maximumAcceptableAge: LocalDateTime): Map[RW, Set[K]] =
      cacheStateMap.flatMap { (cache, state) =>
        val outdated = keys.filter(key => state.remoteValues.get(key).forall(_.timestampFetched.isBefore(maximumAcceptableAge)))
        Option.when(outdated.nonEmpty)(cache -> outdated)
      }
  }

  case class RemoteValue[K, V](value: V, timestampFetched: LocalDateTime) {
    def map[V2](func: V => V2): RemoteValue[K, V2] = RemoteValue[K, V2](func(value), timestampFetched)
  }
  case class CacheReportForKey[K, V, RW <: RemoteReaderWriter[K, V]](key: K, lastKnownValues: Map[RW, RemoteValue[K, V]])
}
