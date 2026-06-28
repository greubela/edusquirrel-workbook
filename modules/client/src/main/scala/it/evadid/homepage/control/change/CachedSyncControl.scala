package it.evadid.homepage.control.change

import it.evadid.core.datastructures.state.storage.AsyncDataCache
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.LoggingLevel.INFO
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.model.interaction.sync.SyncControl
import it.evadid.workbook.model.interaction.sync.SyncInformation.{SyncCache, SyncInformationWithContext}
import it.evadid.workbook.model.interaction.variable.{InteractionVariable, InteractionVariableHistorySerialized}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


case class CachedSyncControl(fullInfo: FullInfo) extends SyncControl {


  private given ExecutionContext = ExecutionContext.global


  private val requestCache: AsyncDataCache[SyncInformationWithContext, SyncCache] = {
    new AsyncDataCache[SyncInformationWithContext, SyncCache](fullInfo.loggerSystemInfo.syncCacheLogger) {

      override protected def executeLoading(in: SyncInformationWithContext)(ec: ExecutionContext): Future[SyncCache] = syncLock.synchronized {
        in.fetchAllFrom()
      }

      override protected def formatInputForLogging(in: SyncInformationWithContext): String = syncLock.synchronized {
        s"SyncInfoWithContext(${in.usageContext})"
      }

      override protected def formatOutputForLogging(out: SyncCache): String = syncLock.synchronized {
        s"SyncCache(${out.createdAt}: ${out.values.size} values)"
      }
    }
  }

  private def executeLoadAll(maxAge: LocalDateTime = LocalDateTime.now()): Future[Map[SyncInformationWithContext, Either[Throwable, SyncCache]]] = requestCache.syncLock.synchronized {
    requestCache.loadAllAsFuture(fullInfo.current.currentSyncSources, maxAge)
  }

  def requestFetchAll(variables: List[InteractionVariable[?]], maxCacheAge: LocalDateTime): Unit = requestCache.syncLock.synchronized {
    if (variables.nonEmpty) requestFetch(variables.head, maxCacheAge).onComplete(res => {
      requestFetchAll(variables.tail, maxCacheAge)
    })
  }

  override def requestFetch(interactionVariable: InteractionVariable[?], maxCacheAge: LocalDateTime): Future[?] = requestCache.syncLock.synchronized {

    def format(in: SyncInformationWithContext, out: Either[Throwable, SyncCache]): String = out.match {
      case Left(err) => "Failure(" + in.syncSource.getClass.getSimpleName + " -> " + err + ")"
      case Right(err) => "Success(" + in.syncSource.getClass.getSimpleName + " -> " + err.values.size + " elements)"
    }

    val futMap: Future[Map[SyncInformationWithContext, Either[Throwable, SyncCache]]] = executeLoadAll(maxCacheAge)
    futMap.onComplete {
      case Success(resMap) => {
        val formatted: String = resMap.map(format).mkString("FetchResults(", ",", ")")
        fullInfo.loggerSystemInfo.syncCacheLogger.logInfo("successfully executed fetch : " + formatted)
        resMap.flatMap(_._2.toOption).foreach(cache => interactionVariable.executeLoad(List(cache)))
      }
      case Failure(exception) => {
        fullInfo.loggerSystemInfo.syncCacheLogger.logExceptionWarn("ignoring date after error during fetching", exception)
      }
    }
    futMap
  }

  override def requestStore[T](from: InteractionVariable[T], forcePush: Boolean): Unit = requestCache.syncLock.synchronized {
    fullInfo.current.currentSyncSources.foreach(curInfo => requestStore(curInfo, from, forcePush))
  }

  def requestStoreAll(interactionVariable: List[InteractionVariable[?]], forcePush: Boolean): Future[Unit] = requestCache.syncLock.synchronized {
    Future.traverse(interactionVariable)(intVar => requestStoreAll(intVar, forcePush)).map(theList => {})
  }

  def requestStoreAll(interactionVariable: InteractionVariable[?], forcePush: Boolean): Future[Unit] = requestCache.syncLock.synchronized {
    Future.traverse(fullInfo.current.currentSyncSources)((currentSyncSource: SyncInformationWithContext) => {
      requestStore(currentSyncSource, interactionVariable, forcePush)
    }).map(theList => {})
  }

  def requestStore(syncSource: SyncInformationWithContext, interactionVariable: InteractionVariable[?], forcePush: Boolean): Future[?] = requestCache.syncLock.synchronized {
    val historyAtRequest = interactionVariable.history // this requests the state, should be consistent across transaction

    val syncContext = syncSource.usageContext.toSyncContext(interactionVariable.keyForSerialization)
    val cacheMap: Option[SyncCache] = requestCache.getSyncIfInCache(syncSource, false)
    val cache: Option[InteractionVariableHistorySerialized] = cacheMap.flatMap(_.values.get(syncContext))

    // calc msg info
    val historySerialized: InteractionVariableHistorySerialized = historyAtRequest.serializedWithStrategy(syncSource.syncStrategy, interactionVariable.underlyingInteraction.serializer)


    val (cacheInfo, shouldFetchBecauseOfCache): (String, Boolean) = {
      if (cacheMap.isEmpty) ("No elements in cache yet", true)
      else if (cacheMap.get.values.isEmpty) (s"Cache has not elements yet (created at${cacheMap.get.createdAt})", true)
      else if (!cacheMap.get.values.contains(syncContext)) {
        val valueCount: Int = cacheMap.get.values.size
        val lastKnownInCache: LocalDateTime = cache.get.lastState.timestamp
        val cacheTime: LocalDateTime = cacheMap.get.createdAt
        (s"Cache does not contain $syncContext ($valueCount elements, created at $cacheTime, last state timestamp: $lastKnownInCache)", true)
      } else {
        val lastKnownInCache: LocalDateTime = cache.get.lastState.timestamp
        val lastToStore: LocalDateTime = historySerialized.lastState.timestamp
        if (lastToStore.isAfter(lastKnownInCache)) (s"lastToStore is after lastKnownInCache: $lastToStore > $lastKnownInCache", true)
        else (s"lastToStore is before lastKnownInCache: $lastToStore < $lastKnownInCache", false)
      }
    }

    def fulLoggerMsg(willExecute: Boolean, reasoning: Option[String]): String = {
      val serializedMsg: String = {
        val skippedEvents: Int = interactionVariable.history.events.size - historySerialized.states.size
        s"serialized $historySerialized events with ${syncSource.syncStrategy} strategy ($skippedEvents skipped, latest ${historySerialized.lastState})"
      }
      val storeMsgFinished: String = s"${syncContext.keyForSerialisation} to ${syncSource.syncSource.getClass.getSimpleName}"
      val reasoningFormatted: String = reasoning.map(": " + _).getOrElse("")
      val firstLine: String = if (willExecute) s"Now Storing $storeMsgFinished$reasoning" else s"Skipp Storing $storeMsgFinished$reasoning"
      val fullMsg: String = {
        s"""
           |$firstLine
           |    cache info: $cacheInfo
           |    serializing info: $serializedMsg
           |""".stripMargin
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

  }


  override def syncLogger: SyncLogger = requestCache.syncLock.synchronized {
    fullInfo.loggerSystemInfo.syncControlLogger
  }
}
