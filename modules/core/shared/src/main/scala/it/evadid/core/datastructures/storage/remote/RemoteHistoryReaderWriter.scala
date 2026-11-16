package it.evadid.core.datastructures.storage.remote

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.EventSyncStrategy
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.InteractionEvent
import java.time.LocalDateTime
import scala.concurrent.Future

/** Readers query event ranges; writers must idempotently merge events instead of replacing histories. */
trait RemoteHistoryReaderWriter[K, T, GA, SA] {
  def reader: RemoteHistoryDataReader[K, T, GA, SA]
  def writer: RemoteReaderWriter.RemoteDataWriter[K, InteractionVariableHistory[T, GA, SA]]
}

/** Data reader for event-based history. */
trait RemoteHistoryDataReader[K, T, GA, SA] extends RemoteReaderWriter.RemoteDataReader[K, List[InteractionEvent[T, GA, SA]]] {
  /** Both bounds are inclusive. Return the complete range, without pagination/truncation; missing keys mean empty ranges.
    * Apply the strategy to the stored history before applying the timestamp bounds.
    */
  def fetchEventsBetween(logger: SyncLogger, keys: Seq[K], starting: LocalDateTime, until: LocalDateTime,
      strategy: EventSyncStrategy): Future[Map[K, List[InteractionEvent[T, GA, SA]]]]

  def fetchAll(logger: SyncLogger, keys: Set[K], strategy: EventSyncStrategy): Future[Map[K, List[InteractionEvent[T, GA, SA]]]] =
    fetchEventsBetween(logger, keys.toSeq, LocalDateTime.MIN, LocalDateTime.MAX, strategy)

  override def flush(logger: SyncLogger, forceFlush: Boolean): Unit = ()
}
