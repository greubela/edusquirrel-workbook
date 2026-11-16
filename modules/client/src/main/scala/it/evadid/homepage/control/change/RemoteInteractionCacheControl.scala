package it.evadid.homepage.control.change

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.storage.remote.{RemoteHistoryCache, RemoteHistoryReaderWriter, RemoteHistorySyncControl}
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncContext, SyncControl}
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.meta.InteractionVariableSyncReport

import scala.concurrent.{ExecutionContext, Future}

case class RemoteInteractionCacheControl(fullInfo: FullInfo) extends SyncControl {

  private given ec: ExecutionContext = ExecutionContext.global

  // Event-based cache using RemoteHistoryCache
  private val syncControl: RemoteHistorySyncControl[SyncContext, _, _, _, _] = {
    val destinations = fullInfo.signals.syncEnvironmentSignal.now().toSet.flatMap(_.syncDestinations)
    RemoteHistorySyncControl(
      fullInfo.loggerSystemInfo.syncControlLogger,
      destinations,
      EventSyncStrategy.KEEP_MAJOR
    )
  }

  override def storeAll(forceFlush: Boolean): Future[Unit] = fullInfo.synchronized {
    syncControl.storeAll(forceFlush)
  }

  override def fetchAll(forceFlush: Boolean): Future[Unit] = fullInfo.synchronized {
    syncControl.fetchAll(forceFlush)
  }

  override def store[T, GA, SA](interactionVariable: InteractionVariable[T, GA, SA], forceFlush: Boolean): Future[Unit] = fullInfo.synchronized {
    syncControl.store(interactionVariable, forceFlush)
  }

  override def syncLogger: SyncLogger = fullInfo.loggerSystemInfo.syncControlLogger

  def createReportFor[T, GA, SA](interaction: WorkbookInteractionElementFull[T, GA, SA]): Signal[Option[InteractionVariableSyncReport[?, ?, ?]]] = {
    val reportSignal = syncControl.cache.reports.toOptionSignal()
    val historySignal = interaction.interactionVariable.historySignal
    syncControl.cache.updateReport.toOptionSignal().combineWith(reportSignal).combineWith(historySignal).map((curReportOp, curHistoryReportOp, curHistory) => {
      if (curReportOp.isEmpty || curHistoryReportOp.isEmpty) None else {
        val syncContext = fullInfo.signals.syncEnvironmentSignal.now().map(_.usageContext.toSyncContext(interaction.interactionVariable.keyForSerialization)).getOrElse(SyncContext.empty)
        val curReport = curHistoryReportOp.get
        {
          Some(InteractionVariableSyncReport[T, GA, SA](
            curHistory,
            curReport.getOrElse(syncContext, RemoteHistoryCache.CacheReportForKey(syncContext, Map.empty, Map.empty)),
            syncControl.cacheStrategies.keySet
          ))
        }
      }
    })

  }
}
