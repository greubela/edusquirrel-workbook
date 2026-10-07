package it.evadid.core.datastructures.storage

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.RemoteCacheCollection.{CacheCollectionReport, CacheKey}
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.DataEntryToWriteToServer
import it.evadid.util.logging.LoggingLevel.INFO
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncInformation.*
import it.evadid.workbook.interaction.sync.*
import upickle.legacy.transform

import java.time.LocalDateTime
import scala.Console.err
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
    val previous = context.pendingMutation +: keys.toSeq.flatMap(pendingDestinations.get)
    val finished = Promise[Unit]()
    context.pendingMutation = finished.future
    keys.foreach(key => pendingDestinations += key -> finished.future)
    val result = Future.sequence(previous).transformWith(_ => Try(operation).fold(Future.failed, identity))
    result.onComplete { _ => syncLock.synchronized {
      finished.trySuccess(())
      keys.foreach { key =>
        if pendingDestinations.get(key).contains(finished.future) then pendingDestinations -= key
      }
    }}
    result
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

  private def refreshCaches(context: CacheContext, maxAge: LocalDateTime): Future[Unit] = {
    val promise: Promise[Unit] = Promise[Unit]()
    val stateNow = context.collection
    val stateAfter = stateNow.ensureCachesAreAtLeastThisRecent(maxAge)
    stateAfter.onComplete {
      case Success(newState) =>
        publish(context, newState)
        promise.success(())
      case Failure(err) =>
        logger.logExceptionWarn("RemoteCacheController: Error during ensureCachesAreAtLeastThisRecent, ignoring update", err)
        promise.failure(err)
    }

    promise.future
  }

  /*
  println(s"ensureMaxAgeSafe(${maxAge}")
  val res: Future[?] = cacheState.updateAsyncUnsafe(_.ensureCachesAreAtLeastThisRecent(maxAge))(ExecutionContext.global).transform(_ => Success(()))
  res
}*/

  def requestCacheDependentUpdate(func: CK => LocalDateTime): Future[?] = syncLock.synchronized {
    val context = currentContext
    enqueue(context) {
      context.collection.requestCacheDependentUpdate(func).transform {
      case Success(newCache) =>
        publish(context, newCache)
        Success( () )
      case Failure(err) =>
        logger.logExceptionWarn("error during cache dependent update", err)
        Failure(err)
      }
    }

  }

  def requestCacheDependentStore(func: CK => List[DataEntryToWriteToServer[K, D]]): Future[?] = syncLock.synchronized {
    val context = currentContext
    enqueue(context) {
      context.collection.requestCacheDependentStore(func).transform {
      case Success(newCache) =>
        publish(context, newCache)
        Success( () )
      case Failure(err) =>
        logger.logExceptionWarn("error during store", err)
        Failure(err)
      }
    }
  }

  def requestStore(writeRequested: List[DataEntryToWriteToServer[K, D]]): Future[?] = if (writeRequested.isEmpty) Future.successful(()) else syncLock.synchronized {
    requestCacheDependentStore(_ => writeRequested)
  }

}

object RemoteCacheController {

  //def apply[K, D, CK <: CacheKey[K, D]](logger: Logger, cacheKeys: List[CK]): RemoteCacheController[K, D, CK] = new RemoteCacheController(logger, ConstantValueObservable(cacheKeys))

  /*
    override def requestStore(from: List[InteractionVariable[?]], forcePush: Boolean, requestTime: LocalDateTime): Future[?] = requestCache.syncLock.synchronized {
      //syncLogger.log(s"Cache Update requested for time ${requestTime}", INFO, Some(false))
      requestCache
        .ensureCacheIsAtLeastThisRecent(fullInfo.current.currentSyncSources, requestTime)
        .recover { _ => () }
        .flatMap(_ => handleStoreRequests(from, fullInfo.current.currentSyncSources, forcePush))
    }

    private def handleStoreRequests(from: List[InteractionVariable[?]], sources: List[SyncInformationWithContext], forcePush: Boolean): Future[?] = {
      val combinations: List[(InteractionVariable[?], SyncInformationWithContext)] = for {
        a <- from
        b <- sources
      } yield (a, b)
      Future.traverse(combinations)(tup => handleStoreRequest(tup._2, tup._1, forcePush).recover { (err: Throwable) =>
        syncLogger.logExceptionWarn(s"Ignoring Store Request for ${tup._1.keyForSerialization} @ ${tup._2} because of exception", err)
      })
    }


    private def handleStoreRequest[T](syncSource: SyncInformationWithContext, interactionVariable: InteractionVariable[T], forcePush: Boolean): Future[?] = requestCache.syncLock.synchronized {

      try {
        // syncLogger.log(s"Store requested for variable ${interactionVariable.keyForSerialization} and destination $syncSource", INFO, Some(true))
        val historyAtRequest: InteractionVariableHistory[T] = interactionVariable.history // this requests the state, should be consistent across transaction
        val historySerialized: InteractionVariableHistorySerialized = historyAtRequest.serializedWithStrategy(syncSource.syncStrategy, interactionVariable.underlyingInteraction.serializer)

        val syncContext: SyncContext = syncSource.usageContext.toSyncContext(interactionVariable.keyForSerialization)
        //val areElementsCachedSufficientlyRecently: Boolean = requestCache.doesCacheContainAllElementsBefore(syncSource, historySerialized.lastState.timestamp)

        // calc msg info

        val lastStoredValue: Option[LocalDateTime] = requestCache.lastStoredValue(syncSource, syncContext)
        val lastInteraction: Option[LocalDateTime] = requestCache.lastInteraction(syncSource)
        val lastToSync: Option[LocalDateTime] = historySerialized.lastStateOption.map(_.timestamp)

        val (cacheInfo, shouldFetchBecauseOfCache): (String, Boolean) = try {
          if (lastToSync.isEmpty || historySerialized.states.isEmpty) ("no data to sync", false)
          else if (lastInteraction.isEmpty || lastStoredValue.isEmpty) ("cache is currently empty", true)
          else if (lastInteraction.get.isBefore(lastToSync.get)) (s"cache outdated, lastToSync > lastCacheUpdate: ${lastToSync.get} > ${lastInteraction.get}", true)
          else if (lastStoredValue.get.isBefore(lastToSync.get)) (s"cache outdated, lastToSync > lastStoredValue: ${lastToSync.get} > ${lastStoredValue.get}", true)
          else (s"no sync necessary, lastToSync <= lastStoredValue: ${lastToSync.get} < ${lastStoredValue.get}", false)
        } catch case (e: Exception) => (s"error during cache check: ${e.getMessage}", true)

        def fulLoggerMsg(willExecute: Boolean, reasoning: Option[String]): String = {
          val serializedMsg: String = {
            val skippedEvents: Int = interactionVariable.history.events.size - historySerialized.states.size
            val latestEvent: String = lastToSync.map(ts => s", latest event: ${ts.toString}").getOrElse("")
            s"serialized ${historySerialized.states.size} events with ${syncSource.syncStrategy} strategy ($skippedEvents skipped$latestEvent)"
          }
          val storeMsgFinished: String = s"${syncContext.keyForSerialisation} to ${syncSource.syncSource.getClass.getSimpleName}"
          val reasoningFormatted: String = reasoning.map(": " + _).getOrElse("")
          val firstLine: String = if (willExecute) s"Now Storing $storeMsgFinished$reasoningFormatted" else s"Skipp Storing $storeMsgFinished$reasoningFormatted"
          val fullMsg: String = {
            s"""$firstLine
               |    cache info: $cacheInfo
               |    serializing info: $serializedMsg
               |""".stripMargin.trim
          }
          fullMsg
        }

        // actual cache logic

        if (forcePush) {
          syncLogger.log(fulLoggerMsg(true, Some("push forced by request")), INFO, Option(true))
          syncSource.syncSource.storeTo(syncContext, historySerialized, syncSource.formatter)
        } else if (shouldFetchBecauseOfCache) {
          syncLogger.log(fulLoggerMsg(true, Some("Cache requires push")), INFO, Option(true))
          syncSource.syncSource.storeTo(syncContext, historySerialized, syncSource.formatter)
        } else {
          syncLogger.log(fulLoggerMsg(false, Some("local cache indicates destination is up to date")), INFO, Option(true))
          Future.successful(())
        }
      } catch case (e: Throwable) => {
        syncLogger.logExceptionWarn(s"Ignoring Store Request for ${interactionVariable.keyForSerialization} @ ${syncSource} because of exception", e)
        e.printStackTrace()
        Future.failed(e)
      }
    }

    override def syncLogger: SyncLogger = requestCache.syncLock.synchronized {
      fullInfo.loggerSystemInfo.syncControlLogger
    }

    def fetchAndStore(intVars: List[InteractionVariable[?]], requestTime: LocalDateTime = LocalDateTime.now()): Future[?] = {
      requestFetch(intVars, requestTime).transformWith((any: Try[?]) => requestStore(intVars, false, requestTime))
    }

    def downloadAllAvailableData(): Unit = fullInfo.current.workbookUserData.foreach(_.downloadAllData())
  */

}
