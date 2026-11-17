package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.storage.remote.{RemoteHistoryDataReader, RemoteHistoryDataWriter, RemoteHistoryReaderWriter}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.model.{FetchResponse, InteractionHistoryJson}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.InteractionEvent
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

/** Codec boundary between a typed event cache and a self-describing destination. */
case class FormattedSyncDestination[F <: InteractionFormat](information: SyncInformationWithContext, format: F)
    extends RemoteHistoryReaderWriter[SyncContext, F] {
  private given ExecutionContext = ExecutionContext.global
  private val handler = information.handler
  private val strategy = information.syncInformation.syncStrategy

  val reader: RemoteHistoryDataReader[SyncContext, F] = new RemoteHistoryDataReader[SyncContext, F] {
    def fetchEventsBetween(logger: SyncLogger, keys: Seq[SyncContext], starting: LocalDateTime, until: LocalDateTime,
        selection: EventSyncStrategy): Future[Map[SyncContext, List[InteractionEvent[F]]]] =
      handler.readHistoriesInRange(information.usageContext, keys, starting, until, selection).map(_.map { (key, history) =>
        key -> history.typedAs(format).events.values.toList
      })

    override def flush(logger: SyncLogger, forceFlush: Boolean): Unit = handler.flush(forceFlush)
  }

  val writer: RemoteHistoryDataWriter[SyncContext, F] = new RemoteHistoryDataWriter[SyncContext, F] {
    def writeForKey(logger: SyncLogger, key: SyncContext, value: InteractionVariableHistory[F]): Future[SyncSuccess] = writeAll(logger, Map(key -> value))
    def writeAll(logger: SyncLogger, values: Map[SyncContext, InteractionVariableHistory[F]]): Future[SyncSuccess] =
      handler.storeHistoriesJson(values.map((key, history) => key -> InteractionHistoryJson.fromHistory(format, history).serialized))
    override def flush(logger: SyncLogger, forceFlush: Boolean): Unit = handler.flush(forceFlush)
  }
}
