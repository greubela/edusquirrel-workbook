package it.evadid.homepage.control.change

import com.raquo.laminar.api.L.{Signal, eventPropToProcessor}
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.workbook.interaction.sync.UsageContext
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.storage.remote.RemoteReaderWriter
import it.evadid.homepage.control.model.FullInfo
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncContext, SyncControl, SyncInformationWithContext}
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.meta.InteractionVariableSyncReport

import scala.concurrent.{ExecutionContext, Future}

/** Event-based sync control using SyncInformationWithContext destinations. */
case class RemoteInteractionCacheControl(fullInfo: FullInfo) extends SyncControl {

  private given ec: ExecutionContext = ExecutionContext.global

  // Event-based cache using SyncInformationWithContext directly
  private val syncDestinations: Set[SyncInformationWithContext] = {
    fullInfo.signals.syncEnvironmentSignal.now().toSet.flatMap(_.syncDestinations)
  }

  override def storeAll(forceFlush: Boolean): Future[Unit] = fullInfo.synchronized {
    Future.traverse(syncDestinations)(destination => {
      destination.writer.flush(fullInfo.loggerSystemInfo.syncControlLogger, forceFlush)
      Future.unit
    }).map(_ => ())
  }

  override def fetchAll(forceFlush: Boolean): Future[Unit] = fullInfo.synchronized {
    Future.traverse(syncDestinations)(destination => {
      destination.reader.flush(fullInfo.loggerSystemInfo.syncControlLogger, forceFlush)
      Future.unit
    }).map(_ => ())
  }

  override def store[T, GA, SA](interactionVariable: InteractionVariable[T, GA, SA], forceFlush: Boolean): Future[Unit] = fullInfo.synchronized {
    val key = interactionVariable.keyForSerialization
    val history = interactionVariable.currentHistory.cleanWithStrategy(EventSyncStrategy.KEEP_MAJOR)
    
    Future.traverse(syncDestinations)(destination => {
      val env = fullInfo.signals.syncEnvironmentSignal.now()
      val usageCtx = env.map(_.usageContext).getOrElse(UsageContext("default", "default", "default"))
      val syncCtx = SyncContext(usageCtx.programId, usageCtx.scenarioId, usageCtx.userId, key)
      destination.writer.writeForKey(fullInfo.loggerSystemInfo.syncControlLogger, syncCtx, "")
    }).map(_ => ())
  }

  override def syncLogger: SyncLogger = fullInfo.loggerSystemInfo.syncControlLogger

  def createReportFor[T, GA, SA](interaction: WorkbookInteractionElementFull[T, GA, SA]): Signal[Option[InteractionVariableSyncReport[SyncContext, T, GA, SA, _]]] = {
    Signal.fromValue(None)
  }
}
