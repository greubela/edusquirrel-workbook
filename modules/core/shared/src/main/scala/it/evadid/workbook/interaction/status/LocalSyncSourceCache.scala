package it.evadid.workbook.interaction.status

import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.util.logging.Logger
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.SyncStatus
import it.evadid.workbook.interaction.sync.SyncContext
import it.evadid.workbook.interaction.sync.SyncInformation.{SyncCache, SyncInformationWithContext}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistorySerialized}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

/*
case class LocalSyncSourceCache(logger: Logger) extends AsyncDataCache[SyncInformationWithContext, SyncCache](logger) {

  private given ExecutionContext = ExecutionContext.global

  def ensureCacheIsAtLeastThisRecent(forInfos: List[SyncInformationWithContext], maximumAge: LocalDateTime): Future[?] = Future.traverse(forInfos)(ensureCacheIsAtLeastThisRecent(_, maximumAge))

  def ensureCacheIsAtLeastThisRecent(forInfo: SyncInformationWithContext, maximumAge: LocalDateTime): Future[?] = {
    if (doesCacheContainElementsAfter(forInfo, maximumAge)) {
      //logger.logInfo(s"Cache recency ensured, cache for ${forInfo} is up to Date: ${lastInteraction(forInfo)}")
      Future.successful(())
    }
    else {
      logger.logInfo(s"Cache for ${forInfo} is outdated (${lastInteraction(forInfo)}), requesting update!")
      loadAsFuture(forInfo, maximumAge).recover { (err: Throwable) => ()
        // logger.logExceptionWarn("cache update failed", err)
      }
    }
  }


  def doesCacheContainElementsAfter(forInfo: SyncInformationWithContext, requireElementNewerThan: LocalDateTime): Boolean = syncLock.synchronized {
    val lastInteractionOp: Option[LocalDateTime] = lastInteraction(forInfo)
    lastInteractionOp.exists(age => age.isAfter(requireElementNewerThan))
  }

  private def cacheLastUpdatedTime(forInfo: SyncInformationWithContext): Option[LocalDateTime] = syncLock.synchronized {
    val syncOp: Option[SyncCache] = cacheCopy().get(forInfo)
    syncOp.map(_.createdAt)
  }

  def lastInteraction(forInfo: SyncInformationWithContext): Option[LocalDateTime] = syncLock.synchronized {
    (cacheLastUpdatedTime(forInfo).toList ++ cacheStateTimes(forInfo).maxOption.toList).maxOption
  }

  def lastStoredValue(forInfo: SyncInformationWithContext, syncContext: SyncContext): Option[LocalDateTime] = {
    val cp: Map[SyncInformationWithContext, SyncCache] = cacheCopy()
    val syncOp: Option[SyncCache] = cp.get(forInfo)
    val history: Option[InteractionVariableHistorySerialized] = syncOp.flatMap(_.contextMaps.get(syncContext))
    history.flatMap(_.lastStateOption).map(_.timestamp)
  }

  def cacheStateTimes(forInfo: SyncInformationWithContext): List[LocalDateTime] = {
    val syncOp: Option[SyncCache] = cacheCopy().get(forInfo)
    val states: List[InteractionVariableHistorySerialized] = syncOp.iterator.flatMap(_.contextMaps.values).toList
    val res: List[LocalDateTime] = states.flatMap(_.states.map(_.timestamp))
    res
  }

  override protected def executeLoading(in: SyncInformationWithContext)(ec: ExecutionContext): Future[SyncCache] = syncLock.synchronized {
    in.fetchAllFrom()
  }


  override protected def formatInputForLogging(in: SyncInformationWithContext): String = syncLock.synchronized {
    s"SyncInfoWithContext(${in.syncSource.getClass.getSimpleName}->${in.usageContext})"
  }

  override protected def formatOutputForLogging(out: SyncCache): String = syncLock.synchronized {
    s"SyncCache(${out.createdAt}: ${out.contextMaps.size} values)"
  }


}
*/






