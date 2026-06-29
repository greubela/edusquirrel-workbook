package it.evadid.homepage.control.change

import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.LoggingLevel.INFO
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.model.interaction.sync.SyncInformation.{SyncCache, SyncInformationWithContext}
import it.evadid.workbook.model.interaction.sync.{SyncContext, SyncControl}
import it.evadid.workbook.model.interaction.variable.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


case class CachedSyncControl(fullInfo: FullInfo) extends SyncControl {

  private given ExecutionContext = ExecutionContext.global

  val requestCache = LocalSyncSourceCache(fullInfo.loggerSystemInfo.syncCacheLogger, fullInfo)

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
        if (lastToSync.isEmpty) ("no data to sync", false)
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
}
