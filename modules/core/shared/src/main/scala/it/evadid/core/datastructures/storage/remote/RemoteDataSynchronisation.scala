package it.evadid.core.datastructures.storage.remote

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.remote.RemoteDataSynchronisation.{CacheReportForKey, RemoteCacheCollectionState, RemoteValue}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.model.FetchResponse

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


case class RemoteDataSynchronisation[K, V, RW <: RemoteReaderWriter[K, V]](
                                                                            syncLogger: SyncLogger,
                                                                            readWriters: Set[RW]
                                                                          ) {

  private given ec: ExecutionContext = ExecutionContext.global


  private val innerState: State[RemoteCacheCollectionState[K, V, RW]] = syncLock.synchronized {
    val maps = readWriters.map(curRW => curRW -> RemoteCacheState.unknownState[K, V]).toMap
    State(RemoteCacheCollectionState(maps))
  }

  private lazy val lastUpdateState: State[Map[K, RemoteValue[K, V]]] = State(Map())

  lazy val updateReport: ObservableValue[Map[K, V]] = lastUpdateState.observable.deriveValue(
    updateMap => updateMap.toList.map((curKey, remVal) => curKey -> remVal.value).toMap
  )

  private val syncLock: State[RemoteCacheCollectionState[K, V, RW]] = innerState

  lazy val reports: ObservableValue[Map[K, CacheReportForKey[K, V, RW]]] = syncLock.synchronized {
    innerState.observable.deriveValue(curStateMap => {
      curStateMap.allKnownKeys.map(curKey => {
        val lastKnownValues: List[(RW, RemoteValue[K, V])] = curStateMap.cacheStateMap.toList.flatMap((curCache, cacheState) => {
          cacheState.remoteValues.get(curKey).map(curValue => curCache -> curValue)
        })
        curKey -> CacheReportForKey(curKey, lastKnownValues.toMap)
      }).toMap
    })
  }

  def updateEverythingKnown(): Future[Unit] = syncLock.synchronized {
    ensureMaxAge(innerState.now().allKnownKeys, LocalDateTime.now(), true)
  }

  private def updateCache(cache: RW)(updateFunc: RemoteCacheState[K, V] => RemoteCacheState[K, V]): Unit = syncLock.synchronized {
    innerState.update(_.updateCache(cache)(updateFunc))
  }


  private def onReadFinished(cache: RW, requestedKeys: Set[K], result: Try[FetchResponse[K, V]]): Unit = syncLock.synchronized {
    result.match {
      case Success(data) => {
        updateCache(cache)(_.newInformationReceived(data.asMap))
        lastUpdateState.set(data.asMap)
      }
      case Failure(exception) => {
        val keyInfoStr = requestedKeys.mkString("[", ", ", "]").take(60) + "..."
        val errMsg = s"ignoring fetch from cache ${cache} for ${requestedKeys.size} (${keyInfoStr}): ${exception.getMessage}"
        syncLogger.logExceptionWarn(errMsg, exception)
      }
    }
  }

  /*def ensureMaxAge(key: K, maximumAcceptableAge: LocalDateTime , forceFlush:): Future[Unit] = syncLock.synchronized {
    val keyOutdatedInCaches = innerState.now().cachesWhereKeyIsOlder(key, maximumAcceptableAge)
    Future.traverse(keyOutdatedInCaches)(curCache => executeFetch(curCache, Set(key), forceFlush)).map(_ => ())
  }*/

  def ensureMaxAge(ofKeys: Set[K], maximumAcceptableAge: LocalDateTime = LocalDateTime.now(), forceFlush: Boolean = false): Future[Unit] = syncLock.synchronized {
    val outdatedKeysPerCache = innerState.now().getOutdatedKeysByCache(maximumAcceptableAge)
    Future.traverse(outdatedKeysPerCache.keySet)(curCache => executeFetch(curCache, outdatedKeysPerCache(curCache).toSet, forceFlush)).map(_ => ())
  }

  def requestStore(cache: RW, dataToStore: Map[K, V], maximumAcceptableAge: LocalDateTime = LocalDateTime.now(), forceFlush: Boolean = false): Future[Unit] = syncLock.synchronized {
    val outdatedKeysPerCache = innerState.now().getOutdatedKeysByCache(maximumAcceptableAge)
    executeStore(cache, outdatedKeysPerCache(cache).toSet.intersect(dataToStore.keySet).map(curKey => curKey -> dataToStore(curKey)).toMap, forceFlush)
  }

  def requestStoreToAll(dataToStore: Map[K, V], maximumAcceptableAge: LocalDateTime = LocalDateTime.now(), forceFlush: Boolean = false): Future[Unit] = syncLock.synchronized {
    val outdatedKeysPerCache = innerState.now().getOutdatedKeysByCache(maximumAcceptableAge)
    Future.traverse(outdatedKeysPerCache.keySet)(curCache => {
      executeStore(curCache, outdatedKeysPerCache(curCache).toSet.intersect(dataToStore.keySet).map(curKey => curKey -> dataToStore(curKey)).toMap, forceFlush)
    }).map(_ => ())
  }

  private def executeFetch(cache: RW, keys: Set[K], forceFlush: Boolean): Future[Unit] = {
    if (keys.nonEmpty) {
      updateCache(cache)(_.fetchRequested(LocalDateTime.now()))
      val res = cache.reader.fetchByKeys(syncLogger, keys)
      res.onComplete(result => onReadFinished(cache, keys, result))(using ExecutionContext.global)
      cache.reader.flush(syncLogger, forceFlush)
      res.map(_ => ())
    } else {
      Future.successful(())
    }
  }

  private def executeStore(cache: RW, dataToUpdate: Map[K, V], forceFlush: Boolean): Future[Unit] = {
    if (dataToUpdate.nonEmpty) {
      updateCache(cache)(_.storeRequested(LocalDateTime.now()))
      val res = cache.writer.writeAll(syncLogger, dataToUpdate)
      cache.writer.flush(syncLogger, forceFlush)
      res.map(_ => ())
    } else {
      Future.successful(())
    }
  }

  def flush(forceFlush: Boolean = true): Unit = {
    readWriters.foreach(curCache => {
      curCache.writer.flush(syncLogger, forceFlush)
      curCache.reader.flush(syncLogger, forceFlush)
    })
  }

}

object RemoteDataSynchronisation {

  case class RemoteCacheCollectionState[K, V, RW <: RemoteReaderWriter[K, V]](cacheStateMap: Map[RW, RemoteCacheState[K, V]]) {
    lazy val allKnownKeys: Set[K] = {
      cacheStateMap.toList.flatMap(curCache => curCache._2.remoteValues.keySet).toSet
    }

    def updateCache(cache: RW)(updateFunc: RemoteCacheState[K, V] => RemoteCacheState[K, V]): RemoteCacheCollectionState[K, V, RW] = {
      val cacheState = cacheStateMap.getOrElse(cache, RemoteCacheState.unknownState[K, V])
      RemoteCacheCollectionState(cacheStateMap.updated(cache, updateFunc(cacheState)))
    }

    def cachesWhereKeyIsOlder(key: K, maximumAcceptableAge: LocalDateTime): Set[RW] = {
      cacheStateMap.keySet.filter(curCache => {
        val cacheState = cacheStateMap(curCache)
        !cacheState.remoteValues.contains(key) || cacheState.remoteValues(key).timestampFetched.isBefore(maximumAcceptableAge)
      })
    }

    def getOutdatedKeysByCache(maximumAcceptableAge: LocalDateTime): Map[RW, Seq[K]] = {
      cacheStateMap.toList.map((curCache, curCacheState) => {
          val outdatedKeys = curCacheState.remoteValues.toList.filter(_._2.timestampFetched.isBefore(maximumAcceptableAge)).map(_._1)
          curCache -> outdatedKeys
        }).filter(_._2.nonEmpty)
        .toMap
    }
  }

  case class RemoteValue[K, V](value: V, timestampFetched: LocalDateTime) {
    def map[V2](func: V => V2): RemoteValue[K, V2] = RemoteValue[K, V2](func(value), timestampFetched)
  }

  case class CacheReportForKey[K, V, RW <: RemoteReaderWriter[K, V]](key: K, lastKnownValues: Map[RW, RemoteValue[K, V]])


}

