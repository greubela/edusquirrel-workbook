package it.evadid.core.datastructures.storage.remote

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.InteractionEvent
import java.time.LocalDateTime
import scala.concurrent.Future

/** Data reader for event-based history. */
trait RemoteHistoryDataReader[K, F <: InteractionFormat] {
  /** Both bounds are inclusive. Return the complete range, without pagination/truncation; missing keys mean empty ranges.
    * Apply the strategy to the stored history before applying the timestamp bounds.
    */
  def fetchEventsBetween(logger: SyncLogger, keys: Seq[K], starting: LocalDateTime, until: LocalDateTime,
      strategy: it.evadid.workbook.interaction.sync.EventSyncStrategy): Future[Map[K, Seq[InteractionEvent[F]]]]

  def fetchAll(logger: SyncLogger, keys: Set[K], strategy: it.evadid.workbook.interaction.sync.EventSyncStrategy): Future[Map[K, Seq[InteractionEvent[F]]]] =
    fetchEventsBetween(logger, keys.toSeq, LocalDateTime.MIN, LocalDateTime.MAX, strategy)

  def flush(logger: SyncLogger, forceFlush: Boolean): Unit = ()
}

/** Data writer for event-based history. */
trait RemoteHistoryDataWriter[K, F <: InteractionFormat] extends RemoteReaderWriter.RemoteDataWriter[K, InteractionVariableHistory[F]] {
  override def flush(logger: SyncLogger, forceFlush: Boolean): Unit = ()
}

/** Reader/Writer for event-based history. */
trait RemoteHistoryReaderWriter[K, F <: InteractionFormat] {
  def reader: RemoteHistoryDataReader[K, F]
  def writer: RemoteHistoryDataWriter[K, F]
}
