package it.evadid.distribution.commandTypes

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.workbook.model.interaction.sync.SyncFormatter.{InteractionSyncRequest, RichInteractionVariableFormatter}
import it.evadid.workbook.model.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.model.interaction.sync.{SyncContext, UsageContext}
import it.evadid.workbook.model.interaction.variable.InteractionVariableHistorySerialized

object SQLCommands {

  trait DbRequest {
    def databaseName: String

    def usageContext: UsageContext

    def hasDatabaseKeyColumn: Boolean

    lazy val formatter: RichInteractionVariableFormatter = RichInteractionVariableFormatter()
  }

  case class StoreToDbRequest(
                               request: InteractionSyncRequest,
                               databaseName: String,
                               hasDatabaseKeyColumn: Boolean
                             ) extends DbRequest {

    lazy val usageContext: UsageContext = request.syncContext.toUsageContext
    lazy val serializedValueString: String = formatter.serialize(request)

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

  val syncToDbCommand: ExecutionCommandFactory[StoreToDbRequest, SyncSuccess] = ExecutionCommandFactory(
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
