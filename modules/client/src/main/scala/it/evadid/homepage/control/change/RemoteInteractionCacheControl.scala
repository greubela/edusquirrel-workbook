package it.evadid.homepage.control.change

import it.evadid.core.datastructures.state.StateHelper.RichSignal
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.storage.RemoteCacheController
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.vm.code.defining.KnownBeDefineStructures.variables
import it.evadid.workbook.interaction.sync.SyncControl.InteractionVariableSyncReport
import it.evadid.workbook.interaction.sync.SyncInformation.SyncInformationWithContext
import it.evadid.workbook.interaction.sync.{SyncContext, SyncControl, SyncInformation, UsageContext}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory, InteractionVariableHistorySerialized}

import java.time
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class RemoteInteractionCacheControl(fullInfo: FullInfo) extends SyncControl {

  private given ExecutionContext = ExecutionContext.global

  private lazy val remoteCache: RemoteCacheController[SyncContext, InteractionVariableHistorySerialized, SyncInformationWithContext] =
    new RemoteCacheController[SyncContext, InteractionVariableHistorySerialized, SyncInformationWithContext](
      SyncLogger(fullInfo.loggerSystemInfo.syncCacheLogger),
      fullInfo.signals.currentSyncDestinationObservable
    ) {
      override def onCacheKeyChange(newKeys: List[SyncInformationWithContext], knownKeys: Set[SyncContext]): Future[?] = {
        println("[UGLY WARN] RemoteInteractionCacheControl:: nothing implemented yet for onCacheKeyChange!")
        Future.successful(())
      }
    }

  override def ensureCachesAreAtLeastThisRecent(maxAge: LocalDateTime): Future[?] = remoteCache.syncLock.synchronized {
    remoteCache.ensureMaxAgeSafe(maxAge)
  }

  override def createCurrentReport[T](forVariable: InteractionVariable[T]): InteractionVariableSyncReport[T] = remoteCache.syncLock.synchronized {
    val syncContext = fullInfo.current.currentHomepageContext.toSyncContext(forVariable.keyForSerialization)
    val res = remoteCache.currentReport(syncContext)
    InteractionVariableSyncReport(forVariable.underlyingInteraction.serializer, forVariable.history, res.cacheStatus)
  }

  override def createObservableReport[T](forVariable: InteractionVariable[T]): ObservableValue[InteractionVariableSyncReport[T]] = remoteCache.syncLock.synchronized {
    val observableContext: ObservableValue[UsageContext] = fullInfo.signals.currentUsageContext.toObservableValue
    val observableHistory: ObservableValue[InteractionVariableHistory[T]] = forVariable.observableState
    val observableCache = remoteCache.observableCache

    observableCache.combineWith(observableContext, observableHistory).deriveValue(tup => {
      val report = tup._1.createReportFor(tup._2.toSyncContext(forVariable.keyForSerialization))
      InteractionVariableSyncReport(forVariable.underlyingInteraction.serializer, tup._3, report.cacheStatus)
    })
  }

  override def ensureCachesContainLastElementsToWrite(variables: List[InteractionVariable[?]]): Future[?]  = remoteCache.syncLock.synchronized {

    def shouldSyncUntil(syncInfo: SyncInformationWithContext): LocalDateTime = variables.flatMap(curVar => {
      val history = curVar.history.serializedWithStrategy(syncInfo.syncStrategy, curVar.underlyingInteraction.serializer)
      history.lastStateOption.map(_.timestamp)
    }).maxOption.getOrElse(LocalDateTime.now())

    remoteCache.requestCacheDependentUpdate(shouldSyncUntil)
  }

  override def requestStore(from: List[InteractionVariable[?]]): Future[?] = remoteCache.syncLock.synchronized {
    remoteCache.requestCacheDependentStore(curSyncInfo => from.flatMap(curSyncInfo.dataToStore(_)))
  }

  override def syncLogger: SyncLogger = fullInfo.loggerSystemInfo.syncControlLogger

  /*
   case class DataEntryToWriteToServer[K, D](dataKey: K, dataValue: D, timestampDataCreated: LocalDateTime) {
      val timestamp: LocalDateTime = timestampDataCreated
    }
    val requestCache = LocalSyncSourceCache(fullInfo.loggerSystemInfo.syncCacheLogger)

    override def requestFetch(variables: List[InteractionVariable[?]], requestTime: LocalDateTime): Future[?] = requestCache.syncLock.synchronized {
      def continue(): Future[?] = requestFetch(variables.tail, requestTime)

      if (variables.isEmpty) Future.successful(())
      else requestFetch(variables.head, requestTime).transformWith {
        case Success(any) => continue()
        case Failure(err) => continue() //syncLogger.logExceptionWarn(s"ignoring date after error during fetching ${variables.head.keyForSerialization}", err) // already printed there
      }
    }

    override def requestFetch(interactionVariable: InteractionVariable[?], requestTime: LocalDateTime): Future[?] = requestCache.syncLock.synchronized {

      def format(in: SyncInformationWithContext, out: Either[Throwable, SyncCache]): String = out.match {
        case Left(err) => "Failure(" + in.syncSource.getClass.getSimpleName + " -> " + err + ")"
        case Right(err) => "Success(" + in.syncSource.getClass.getSimpleName + " -> " + err.contextMaps.size + " elements)"
      }

      val futMap: Future[Map[SyncInformationWithContext, Either[Throwable, SyncCache]]] = requestCache.loadAllAsFuture(fullInfo.current.currentSyncSources, requestTime)
      futMap.onComplete {
        case Success(resMap) => {
          val formatted: String = resMap.map(format).mkString("FetchResults(", ",", ")")
          fullInfo.loggerSystemInfo.syncCacheLogger.logInfo(s"Fetched data for ${interactionVariable.keyForSerialization} (${resMap.keySet.size} sources)")
          resMap.flatMap(_._2.toOption).foreach(cache => interactionVariable.executeLoad(List(cache)))
        }
        case Failure(exception) => {
          fullInfo.loggerSystemInfo.syncCacheLogger.logExceptionWarn("ignoring date after error during fetching", exception)
        }
      }
      futMap
    }
    /*
    STORE
     */

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




   */
}
