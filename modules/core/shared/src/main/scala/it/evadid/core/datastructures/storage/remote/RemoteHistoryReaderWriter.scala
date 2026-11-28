package it.evadid.core.datastructures.storage.remote

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.history.InteractionVariableHistory
import it.evadid.workbook.sync.SyncSuccess
import it.evadid.workbook.history.events.{EventId, InteractionEvent}
import java.time.Instant
import scala.concurrent.Future

/** Data reader for event-based history. */
trait RemoteHistoryDataReader[K, F <: InteractionFormat] {
  /** Both bounds are inclusive. Return the complete range, without pagination/truncation; missing keys mean empty ranges.
    * Apply the strategy to the stored history before applying the timestamp bounds.
    */
  def fetchEventsBetween(logger: SyncLogger, keys: Seq[K], starting: Instant, until: Instant,
      strategy: it.evadid.workbook.sync.EventSyncStrategy): Future[Map[K, Seq[InteractionEvent[F]]]]

  def fetchAll(logger: SyncLogger, keys: Set[K], strategy: it.evadid.workbook.sync.EventSyncStrategy): Future[Map[K, Seq[InteractionEvent[F]]]] =
    fetchEventsBetween(logger, keys.toSeq, Instant.MIN, Instant.MAX, strategy)

  def flush(logger: SyncLogger, forceFlush: Boolean): Unit = ()
}

/** Data writer for event-based history. A result names the sent events stored once the write has committed. */
trait RemoteHistoryDataWriter[K, F <: InteractionFormat] {
  def writeForKey(logger: SyncLogger, key: K, value: InteractionVariableHistory[F]): Future[SyncSuccess[EventId]]

  def writeAll(logger: SyncLogger, values: Map[K, InteractionVariableHistory[F]]): Future[SyncSuccess[EventId]]

  def flush(logger: SyncLogger, forceFlush: Boolean): Unit = ()
}

/** Reader/Writer for event-based history. */
trait RemoteHistoryReaderWriter[K, F <: InteractionFormat] {
  def reader: RemoteHistoryDataReader[K, F]
  def writer: RemoteHistoryDataWriter[K, F]
}
