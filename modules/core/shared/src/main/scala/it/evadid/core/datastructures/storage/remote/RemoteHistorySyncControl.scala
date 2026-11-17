package it.evadid.core.datastructures.storage.remote

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncContext, SyncControl, SyncInformationWithContext}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}
import it.evadid.workbook.interaction.variable.events.InteractionEvent
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

/** Event-based sync control using RemoteHistoryCache. */
case class RemoteHistorySyncControl[K, F <: InteractionFormat, RW <: RemoteHistoryReaderWriter[K, F]](
    syncLogger: SyncLogger,
    cacheStrategies: Map[RW, EventSyncStrategy]
) extends SyncControl {

  private given ec: ExecutionContext = ExecutionContext.global
  
  val cache = RemoteHistoryCache[K, F, RW](syncLogger, cacheStrategies)

  override def storeAll(forceFlush: Boolean): Future[Unit] = cache.storeAll(forceFlush)

  override def fetchAll(forceFlush: Boolean): Future[Unit] = {
    val keys = cache.updateReport.now().getOrElse(Map.empty).keys.toSet
    cache.ensureMaxAge(keys, LocalDateTime.now(), forceFlush)
  }

  override def store[F2 <: InteractionFormat](interactionVariable: InteractionVariable[F2], forceFlush: Boolean): Future[Unit] = {
    val key = interactionVariable.keyForSerialization
    val history = interactionVariable.currentHistory.cleanWithStrategy(EventSyncStrategy.KEEP_MAJOR)
    cache.mergeLocal(Map(key.asInstanceOf[K] -> history.asInstanceOf[InteractionVariableHistory[F]]))
    cache.storeAll(forceFlush)
  }

  override def ensureSynced(): Future[Unit] = fetchAll(true).flatMap(_ => storeAll(true))

}

object RemoteHistorySyncControl {
  def apply[F <: InteractionFormat, RW <: RemoteHistoryReaderWriter[SyncContext, F]](
      syncLogger: SyncLogger,
      destinations: Set[RW],
      strategy: EventSyncStrategy = EventSyncStrategy.KEEP_MAJOR
  ): RemoteHistorySyncControl[SyncContext, F, RW] = {
    val cacheStrategies = destinations.map(d => d -> strategy).toMap
    RemoteHistorySyncControl(syncLogger, cacheStrategies)
  }
}
