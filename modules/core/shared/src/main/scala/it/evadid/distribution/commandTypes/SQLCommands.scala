package it.evadid.distribution.commandTypes

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import java.time.LocalDateTime
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.sql.DatabaseConfiguration
import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.sql.CommandLog
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.events.EventId
import upickle.default.*

object SQLCommands {
  private def redactCredentials(payload: ujson.Value): ujson.Value = payload.objOpt.map { fields =>
    val safe = fields.get("databaseConfig").flatMap(value => scala.util.Try(read[DatabaseConfiguration](value)).toOption)
      .map(config => writeJs[DatabaseConfiguration](config.withoutCredentials)).getOrElse(ujson.Obj("redacted" -> true))
    ujson.Obj.from(fields.toList.filterNot(_._1 == "databaseConfig") :+ ("databaseConfig" -> safe))
  }.getOrElse(ujson.Obj("redacted" -> true))


  trait DbRequest {
    def databaseConfig: DatabaseConfiguration

    def databaseFormat: DatabaseHistoryFormat
  }

  case class StoreToDbRequest(
                               syncContext: SyncContext,
                               history: InteractionHistoryJson,
                               databaseConfig: DatabaseConfiguration,
                               databaseFormat: DatabaseHistoryFormat
                             ) extends DbRequest derives ReadWriter {

    lazy val usageContext: UsageContext = syncContext.toUsageContext

  }

  /** Parallel contexts and envelopes preserve each key while sending one request per flush. */
  case class BulkStoreToDbRequest(
      syncContexts: Seq[SyncContext], histories: Seq[InteractionHistoryJson],
      databaseConfig: DatabaseConfiguration, databaseFormat: DatabaseHistoryFormat
  ) extends DbRequest derives ReadWriter {
    require(syncContexts.size == histories.size, "Every history requires exactly one sync context")
    require(syncContexts.distinct.size == syncContexts.size, "Bulk histories must have distinct sync contexts")
    def entries: Seq[StoreToDbRequest] = syncContexts.zip(histories).map((context, history) =>
      StoreToDbRequest(context, history, databaseConfig, databaseFormat))
  }

  /** Range-read: return only events whose timestamp falls in [starting, until]. */
  case class FetchEventsFromDbRequest(
                                       usageContext: UsageContext,
                                       keys: Seq[String],
                                       starting: LocalDateTime,
                                       until: LocalDateTime,
                                       databaseConfig: DatabaseConfiguration,
                                       databaseFormat: DatabaseHistoryFormat,
                                       strategy: EventSyncStrategy
                                     ) extends DbRequest derives ReadWriter {
    require(!until.isBefore(starting), "History range end must not precede its start")
  }


  case class DeleteInDbRequest(
                                usageContext: UsageContext,
                                limitToKey: Option[String],
                                databaseConfig: DatabaseConfiguration,
                                databaseFormat: DatabaseHistoryFormat,
                                allWorkbooks: Boolean = false
                              ) extends DbRequest derives ReadWriter {
    require(!allWorkbooks || limitToKey.isEmpty, "An all-workbook reset cannot be restricted to one variable.")
  }

  case class DbFetchResponse(fetchedElements: Map[SyncContext, InteractionHistoryJson]) derives ReadWriter


  lazy val StoreToDbCommand: ExecutionCommandFactory[StoreToDbRequest, SyncSuccess[EventId]] = ExecutionCommandFactory[StoreToDbRequest, SyncSuccess[EventId]](
    "sync-to-db-request", storeLogIntoTable = Some(CommandLog.syncedLog), privacyFilter = Some(redactCredentials),
    requiresLogin = true
  )

  lazy val bulkStoreToDbCommand: ExecutionCommandFactory[BulkStoreToDbRequest, SyncSuccess[EventId]] =
    ExecutionCommandFactory[BulkStoreToDbRequest, SyncSuccess[EventId]]("sync-histories-to-db-request",
      storeLogIntoTable = Some(CommandLog.syncedLog), privacyFilter = Some(redactCredentials), requiresLogin = true)

  lazy val fetchEventsFromDbCommand: ExecutionCommandFactory[FetchEventsFromDbRequest, DbFetchResponse] = ExecutionCommandFactory[FetchEventsFromDbRequest, DbFetchResponse](
    "fetch-events-from-db-request", storeLogIntoTable = Some(CommandLog.syncedLog), privacyFilter = Some(redactCredentials),
    requiresLogin = true
  )

  // A distinct command prevents an older server from acknowledging a reset
  // while ignoring its all-workbook scope and deleting only the current workbook.
  lazy val resetUserHistoriesDbCommand: ExecutionCommandFactory[DeleteInDbRequest, SyncSuccess[SyncContext]] = ExecutionCommandFactory[DeleteInDbRequest, SyncSuccess[SyncContext]](
    "reset-user-histories-in-db-request", storeLogIntoTable = Some(CommandLog.syncedLog), privacyFilter = Some(redactCredentials),
    requiresLogin = true
  )

  lazy val clearValuesDbCommand: ExecutionCommandFactory[DeleteInDbRequest, SyncSuccess[SyncContext]] = ExecutionCommandFactory[DeleteInDbRequest, SyncSuccess[SyncContext]](
    "clear-values-in-db-request", storeLogIntoTable = Some(CommandLog.syncedLog), privacyFilter = Some(redactCredentials),
    requiresLogin = true
  )

}