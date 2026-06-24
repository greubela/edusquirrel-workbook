package it.evadid.distribution.commandTypes

import it.evadid.core.util.io.serializer.DistributionSerializer
import it.evadid.distribution.command.ExecutionCommandFactory

object SQLCommands {

  case class SyncToDbRequest(
                              programId: String,
                              scenarioId: String,
                              userId: String,
                              eventTime: String,
                              keyId: String,
                              eventData: String
                            )

  case class SyncToDbResponse(rowsAffected: Int)

  case class FetchFromDbRequest(
                                 programId: String,
                                 scenarioId: String,
                                 userId: String,
                                 keyId: Option[String]
                               )

  case class FetchFromDbResponse(values: Map[String, String])

  case class ClearDbRequest(
                             programId: String,
                             scenarioId: String,
                             userId: String,
                             keyId: Option[String]
                           )

  case class ClearDbResponse(rowsAffected: Int)

  val syncToDbCommand: ExecutionCommandFactory[SyncToDbRequest, SyncToDbResponse] = ExecutionCommandFactory(
    "sync-to-db-request",
    DistributionSerializer.serializerSyncToDbRequestJson,
    DistributionSerializer.serializerSyncToDbResponseJson
  )

  val fetchFromDbCommand: ExecutionCommandFactory[FetchFromDbRequest, FetchFromDbResponse] = ExecutionCommandFactory(
    "fetch-from-db-request",
    DistributionSerializer.serializerFetchFromDbRequestJson,
    DistributionSerializer.serializerFetchFromDbResponseJson
  )

  val clearDbCommand: ExecutionCommandFactory[ClearDbRequest, ClearDbResponse] = ExecutionCommandFactory(
    "clear-db-request",
    DistributionSerializer.serializerClearDbRequestJson,
    DistributionSerializer.serializerClearDbResponseJson
  )

}
