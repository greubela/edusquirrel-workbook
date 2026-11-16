package it.evadid.core.datastructures.storage.remote

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncContext, SyncControl, SyncInformationWithContext}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}
import it.evadid.workbook.interaction.variable.events.InteractionEvent
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

/** Event-based sync control using RemoteHistoryCache. */
case class RemoteHistorySyncControl[K, T, GA, SA, RW <: RemoteHistoryReaderWriter[K, T, GA, SA]](
    syncLogger: SyncLogger,
    cacheStrategies: Map[RW, EventSyncStrategy]
) extends SyncControl {

  private given ec: ExecutionContext = ExecutionContext.global
  
  val cache = RemoteHistoryCache[K, T, GA, SA, RW](syncLogger, cacheStrategies)

  override def storeAll(forceFlush: Boolean): Future[Unit] = cache.storeAll(forceFlush)

  override def fetchAll(forceFlush: Boolean): Future[Unit] = {
    val keys = cache.updateReport.now().getOrElse(Map.empty).keys.toSet
    cache.ensureMaxAge(keys, LocalDateTime.now(), forceFlush)
  }

  override def store[T2, GA2, SA2](interactionVariable: InteractionVariable[T2, GA2, SA2], forceFlush: Boolean): Future[Unit] = {
    val key = interactionVariable.keyForSerialization
    val history = interactionVariable.currentHistory.cleanWithStrategy(EventSyncStrategy.KEEP_MAJOR)
    cache.mergeLocal(Map(key.asInstanceOf[K] -> history.asInstanceOf[InteractionVariableHistory[T, GA, SA]]))
    cache.storeAll(forceFlush)
  }

  override def ensureSynced(): Future[Unit] = fetchAll(true).flatMap(_ => storeAll(true))

}

object RemoteHistorySyncControl {
  def apply[T, GA, SA, RW <: RemoteHistoryReaderWriter[SyncContext, T, GA, SA]](
      syncLogger: SyncLogger,
      destinations: Set[RW],
      strategy: EventSyncStrategy = EventSyncStrategy.KEEP_MAJOR
  ): RemoteHistorySyncControl[SyncContext, T, GA, SA, RW] = {
    val cacheStrategies = destinations.map(d => d -> strategy).toMap
    RemoteHistorySyncControl(syncLogger, cacheStrategies)
  }
}
