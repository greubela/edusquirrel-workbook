package it.evadid.distribution.commandTypes

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.workbook.interaction.sync.*
import upickle.default.*

object SQLCommands {

  trait DbRequest {
    def databaseName: String

    def usageContext: UsageContext

    def hasDatabaseKeyColumn: Boolean
  }

  case class StoreToDbRequest(
                               syncContext: SyncContext,
                               historyJson: String,
                               databaseName: String,
                               hasDatabaseKeyColumn: Boolean
                             ) extends DbRequest derives ReadWriter {

    lazy val usageContext: UsageContext = syncContext.toUsageContext

  }

  case class FetchAllFromDbRequest(
                                    usageContext: UsageContext,
                                    databaseName: String,
                                    mayLimitToKey: Option[String],
                                    hasDatabaseKeyColumn: Boolean
                                  ) extends DbRequest derives ReadWriter


  case class DeleteInDbRequest(
                                usageContext: UsageContext,
                                limitToKey: Option[String],
                                databaseName: String,
                                hasDatabaseKeyColumn: Boolean
                              ) extends DbRequest derives ReadWriter {
  }


  case class DbFetchResponse(fetchedElements: Map[SyncContext, String]) derives ReadWriter {


  }

  lazy val StoreToDbCommand: ExecutionCommandFactory[StoreToDbRequest, SyncSuccess] = ExecutionCommandFactory[StoreToDbRequest, SyncSuccess](
    "sync-to-db-request"
  )

  lazy val fetchFromDbCommand: ExecutionCommandFactory[FetchAllFromDbRequest, DbFetchResponse] = ExecutionCommandFactory[FetchAllFromDbRequest, DbFetchResponse](
    "fetch-from-db-request"
  )

  lazy val clearValuesDbCommand: ExecutionCommandFactory[DeleteInDbRequest, SyncSuccess] = ExecutionCommandFactory[DeleteInDbRequest, SyncSuccess](
    "clear-values-in-db-request"
  )

}
