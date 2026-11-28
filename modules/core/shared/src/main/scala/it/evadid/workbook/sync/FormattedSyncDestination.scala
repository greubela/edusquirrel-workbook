package it.evadid.workbook.sync

import it.evadid.core.datastructures.storage.remote.{RemoteHistoryDataReader, RemoteHistoryDataWriter, RemoteHistoryReaderWriter}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.history.InteractionHistory
import it.evadid.workbook.history.events.{EventId, InteractionEvent}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Codec boundary between a typed event cache and a self-describing destination. */
case class FormattedSyncDestination[F <: InteractionFormat](information: SyncInformationWithContext, format: F)
    extends RemoteHistoryReaderWriter[SyncContext, F] {
  private given ExecutionContext = ExecutionContext.global
  private val handler = information.handler
  private val strategy = information.syncInformation.syncStrategy

  val reader: RemoteHistoryDataReader[SyncContext, F] = new RemoteHistoryDataReader[SyncContext, F] {
    def fetchEventsBetween(logger: SyncLogger, keys: Seq[SyncContext], starting: Instant, until: Instant,
        selection: EventSyncStrategy): Future[Map[SyncContext, Seq[InteractionEvent[F]]]] =
      // Each history is typed separately: one unreadable record is discarded without hiding the others.
      handler.readHistoriesInRange(information.usageContext, keys, starting, until, selection).map(_.flatMap { (key, history) =>
        scala.util.Try(history.typedAs(format).events.values.toList).fold(
          error => { logger.logExceptionWarn(s"Discarded the unreadable history of ${key.keyForSerialisation}", error); None },
          events => Some(key -> events))
      })

    override def flush(logger: SyncLogger, forceFlush: Boolean): Unit = handler.flush(forceFlush)
  }

  val writer: RemoteHistoryDataWriter[SyncContext, F] = new RemoteHistoryDataWriter[SyncContext, F] {
    def writeForKey(logger: SyncLogger, key: SyncContext, value: InteractionHistory[F]): Future[SyncSuccess[EventId]] = writeAll(logger, Map(key -> value))
    def writeAll(logger: SyncLogger, values: Map[SyncContext, InteractionHistory[F]]): Future[SyncSuccess[EventId]] =
      handler.storeHistoriesJson(values.map((key, history) => key -> InteractionHistoryJson.fromHistory(format, history).serialized))
    override def flush(logger: SyncLogger, forceFlush: Boolean): Unit = handler.flush(forceFlush)
  }
}
