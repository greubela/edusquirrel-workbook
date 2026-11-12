package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.RemoteCacheCollection.{CacheCollectionReport, CacheKey}
import it.evadid.util.logging.LoggingLevel.INFO
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.model.DataEntryToSync

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

abstract class RemoteCacheController[K, D, CK <: CacheKey[K, D]](logger: SyncLogger, cacheKeys: ObservableValue[List[CK]]) {

  private given ExecutionContext = ExecutionContext.global

  private lazy val syncLockObj: RemoteCacheController[K, D, CK] = this

  def syncLock: Object = syncLockObj

  private val cacheState: State[RemoteCacheCollection[K, D, CK]] = State(RemoteCacheCollection.fromCacheKeys(logger, cacheKeys.now().getOrElse(List())))

  private class CacheContext(var collection: RemoteCacheCollection[K, D, CK]) {
    var pendingMutation: Future[Unit] = Future.successful(())
  }

  private var currentContext = new CacheContext(cacheState.now())
  private var pendingDestinations = Map.empty[CK, Future[Unit]]

  cacheKeys.addObserver(onNewKeys => onCacheKeysChanged(onNewKeys))

  private def enqueue[A](context: CacheContext)(operation: => Future[A]): Future[A] = syncLock.synchronized {
    val keys = context.collection.remoteCaches.keySet
    val finished = Promise[Unit]()
    val previous = reserveDestinations(context, keys, finished.future)
    val result = Future.sequence(previous).transformWith(_ => operation)
    result.onComplete { _ => syncLock.synchronized {
      finished.trySuccess(())
      releaseDestinations(keys, finished.future)
    }}
    result
  }

  private def reserveDestinations(context: CacheContext, keys: Set[CK], completion: Future[Unit]): Seq[Future[Unit]] = {
    val previous = context.pendingMutation +: keys.toSeq.flatMap(pendingDestinations.get)
    context.pendingMutation = completion
    pendingDestinations ++= keys.map(_ -> completion)
    previous
  }

  private def releaseDestinations(keys: Set[CK], completion: Future[Unit]): Unit = {
    keys.foreach { key =>
      // A later queued operation may already own this destination.
      if pendingDestinations.get(key).contains(completion) then pendingDestinations -= key
    }
  }


  lazy val observableCache: ObservableValue[RemoteCacheCollection[K, D, CK]] = cacheState.observable

  def observableReport(key: K): ObservableValue[CacheCollectionReport[K, D, CK]] = {
    observableCache.deriveValue(_.createReportFor(key))
  }

  def currentReport(key: K): CacheCollectionReport[K, D, CK] = {
    cacheState.now().createReportFor(key)
  }

  private def publish(context: CacheContext, collection: RemoteCacheCollection[K, D, CK]): Unit = syncLock.synchronized {
    context.collection = collection
    if context eq currentContext then cacheState.set(collection)
  }

  private def onCacheKeysChanged(newKeys: List[CK]): Future[?] = syncLock.synchronized {
    if currentContext.collection.remoteCaches.keySet == newKeys.toSet then return Future.successful(())
    logger.log(s"Cache keys changed to: ${newKeys}", INFO, None)
    val knownKeys = currentContext.collection.allKnownKeys()
    val context = new CacheContext(RemoteCacheCollection.fromCacheKeys(logger, newKeys))
    currentContext = context
    cacheState.set(context.collection)
    Try(onCacheKeyChange(newKeys, knownKeys)).fold(Future.failed, identity).recover {
      case err: Throwable => logger.logExceptionWarn("error handling cache key change", err)
    }.flatMap(_ => enqueue(context)(refreshCaches(context, LocalDateTime.now())))
  }

  protected def onCacheKeyChange(newKeys: List[CK], knownKeys: Set[K]): Future[?]

  def ensureMaxAgeSafe(maxAge: LocalDateTime): Future[?] = syncLock.synchronized {
    val context = currentContext
    enqueue(context)(refreshCaches(context, maxAge))
  }

  private def publishUpdate(context: CacheContext, errorMessage: String)
                           (operation: => Future[RemoteCacheCollection[K, D, CK]]): Future[Unit] =
    Try(operation).fold(Future.failed, identity).transform {
      case Success(collection) =>
        publish(context, collection)
        Success(())
      case Failure(error) =>
        logger.logExceptionWarn(errorMessage, error)
        Failure(error)
    }

  private def refreshCaches(context: CacheContext, maxAge: LocalDateTime): Future[Unit] =
    publishUpdate(context, "RemoteCacheController: Error during ensureCachesAreAtLeastThisRecent, ignoring update") {
      context.collection.ensureCachesAreAtLeastThisRecent(maxAge)
    }

  def requestCacheDependentUpdate(func: CK => LocalDateTime): Future[?] = syncLock.synchronized {
    val context = currentContext
    enqueue(context) {
      publishUpdate(context, "error during cache dependent update") {
        context.collection.requestCacheDependentUpdate(func)
      }
    }
  }

  def requestCacheDependentStore(func: CK => List[DataEntryToSync[K, D]]): Future[?] = syncLock.synchronized {
    val context = currentContext
    enqueue(context) {
      publishUpdate(context, "error during store") {
        context.collection.requestCacheDependentStore(func)
      }
    }
  }

  def requestStore(writeRequested: List[DataEntryToSync[K, D]]): Future[?] = if (writeRequested.isEmpty) Future.successful(()) else syncLock.synchronized {
    requestCacheDependentStore(_ => writeRequested)
  }

}
