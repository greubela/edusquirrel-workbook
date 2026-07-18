package it.evadid.distribution.commandTypes

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoTyped
import it.evadid.workbook.interaction.sync.{SyncContext, UsageContext}
import it.evadid.workbook.interaction.variable.InteractionVariableHistorySerialized
import it.evadid.workbook.interaction.sync.SyncFormatter.{InteractionSyncRequest, RichInteractionVariableFormatter}
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess

object SQLCommands {

  trait DbRequest {
    def databaseName: String

    def usageContext: UsageContext

    def hasDatabaseKeyColumn: Boolean

    lazy val formatter: RichInteractionVariableFormatter = RichInteractionVariableFormatter()
  }

  case class StoreToDbRequest(
                               syncContext: SyncContext,
                               historySerialized: InteractionVariableHistorySerialized,
                               databaseName: String,
                               hasDatabaseKeyColumn: Boolean
                             ) extends DbRequest {

    lazy val usageContext: UsageContext = syncContext.toUsageContext
    lazy val serializedValueString: String = formatter.serialize(syncContext, historySerialized)

  }

  case class FetchAllFromDbRequest(
                                    usageContext: UsageContext,
                                    databaseName: String,
                                    mayLimitToKey: Option[String],
                                    hasDatabaseKeyColumn: Boolean
                                  ) extends DbRequest


  case class DeleteInDbRequest(
                                usageContext: UsageContext,
                                limitToKey: Option[String],
                                databaseName: String,
                                hasDatabaseKeyColumn: Boolean
                              ) extends DbRequest {
  }


  case class DbFetchResponse(fetchedElements: Map[SyncContext, InteractionVariableHistorySerialized]) {




  }

  val StoreToDbCommand: ExecutionCommandFactory[StoreToDbRequest, SyncSuccess] = ExecutionCommandFactory(
    "sync-to-db-request",
    DefaultSerializer.serializerStoreToDbRequestJson,
    DefaultSerializer.serializerSyncSuccess
  )

  val fetchFromDbCommand: ExecutionCommandFactory[FetchAllFromDbRequest, DbFetchResponse] = ExecutionCommandFactory(
    "fetch-from-db-request",
    DefaultSerializer.serializerFetchAllFromDbRequestJson,
    DefaultSerializer.serializerDbFetchResponse
  )

  val clearValuesDbCommand: ExecutionCommandFactory[DeleteInDbRequest, SyncSuccess] = ExecutionCommandFactory(
    "clear-values-in-db-request",
    DefaultSerializer.serializerDeleteInDbRequestJson,
    DefaultSerializer.serializerSyncSuccess
  )

}
