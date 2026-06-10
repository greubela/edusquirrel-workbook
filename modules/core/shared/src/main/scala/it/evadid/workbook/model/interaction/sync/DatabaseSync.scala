package it.evadid.workbook.model.interaction.sync

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.distribution.commandTypes.SQLCommands.SyncToDbRequest
import it.evadid.util.Logger

import java.time.LocalDateTime

class DatabaseSync(
                    programId: String,
                    userId: String,
                    backendServerExecutor: ExecutionClient
                  ) extends SyncDestination {

  override def syncTo(key: String, value: String): Unit = {
    val request = SyncToDbRequest(programId, userId, key, LocalDateTime.now().toString, value)
    SQLCommands.syncToDbCommand.sendCommandTo(backendServerExecutor, Logger(), request)
    ()
  }

  override def syncAllFrom(): Map[String, String] = ???

  override def syncKeyFrom(key: String): Option[String] = ???


}
