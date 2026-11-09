package it.evadid.distribution.commandTypes

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.sql.DatabaseConfiguration
import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.sql.DatabaseTable
import it.evadid.workbook.interaction.sync.*
import upickle.default.*

object SQLCommands {
  private def redactCredentials(payload: ujson.Value): ujson.Value = payload.objOpt.map { fields =>
    val safe = fields.get("databaseConfig").flatMap(value => scala.util.Try(read[DatabaseConfiguration](value)).toOption)
      .map(config => writeJs[DatabaseConfiguration](config.withoutCredentials)).getOrElse(ujson.Obj("redacted" -> true))
    ujson.Obj.from(fields.toList.filterNot(_._1 == "databaseConfig") :+ ("databaseConfig" -> safe))
  }.getOrElse(ujson.Obj("redacted" -> true))


  trait DbRequest {
    def databaseConfig: DatabaseConfiguration

    def usageContext: UsageContext

    def databaseFormat: DatabaseHistoryFormat
  }

  case class StoreToDbRequest(
                               syncContext: SyncContext,
                               historyJson: String,
                               databaseConfig: DatabaseConfiguration,
                               databaseFormat: DatabaseHistoryFormat
                             ) extends DbRequest derives ReadWriter {

    lazy val usageContext: UsageContext = syncContext.toUsageContext

  }

  case class FetchAllFromDbRequest(
                                    usageContext: UsageContext,
                                    databaseConfig: DatabaseConfiguration,
                                    mayLimitToKey: Option[String],
                                    databaseFormat: DatabaseHistoryFormat
                                  ) extends DbRequest derives ReadWriter


  case class DeleteInDbRequest(
                                usageContext: UsageContext,
                                limitToKey: Option[String],
                                databaseConfig: DatabaseConfiguration,
                                databaseFormat: DatabaseHistoryFormat
                              ) extends DbRequest derives ReadWriter {
  }

  case class DbFetchResponse(fetchedElements: Map[SyncContext, String]) derives ReadWriter {


  }

  lazy val StoreToDbCommand: ExecutionCommandFactory[StoreToDbRequest, SyncSuccess] = ExecutionCommandFactory[StoreToDbRequest, SyncSuccess](
    "sync-to-db-request", storeLogIntoTable = Some(DatabaseTable.syncedLog), privacyFilter = Some(redactCredentials)
  )

  lazy val fetchFromDbCommand: ExecutionCommandFactory[FetchAllFromDbRequest, DbFetchResponse] = ExecutionCommandFactory[FetchAllFromDbRequest, DbFetchResponse](
    "fetch-from-db-request", storeLogIntoTable = Some(DatabaseTable.syncedLog), privacyFilter = Some(redactCredentials)
  )

  lazy val clearValuesDbCommand: ExecutionCommandFactory[DeleteInDbRequest, SyncSuccess] = ExecutionCommandFactory[DeleteInDbRequest, SyncSuccess](
    "clear-values-in-db-request", storeLogIntoTable = Some(DatabaseTable.syncedLog), privacyFilter = Some(redactCredentials)
  )

}
