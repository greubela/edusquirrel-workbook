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

  val syncToDbCommand: ExecutionCommandFactory[SyncToDbRequest, SyncToDbResponse] = ExecutionCommandFactory(
    "sync-to-db-request",
    DistributionSerializer.serializerSyncToDbRequestJson,
    DistributionSerializer.serializerSyncToDbResponseJson
  )

}
