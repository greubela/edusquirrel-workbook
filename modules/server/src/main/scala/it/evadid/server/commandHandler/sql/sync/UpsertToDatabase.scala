package it.evadid.server.commandHandler.sql.sync

import it.evadid.distribution.commandTypes.SQLCommands.StoreToDbRequest
import it.evadid.server.database.DatabaseRequestConnection
import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.SyncSuccess

object UpsertToDatabase {
  def handleRequest(request: StoreToDbRequest, logger: Logger): SyncSuccess =
    DatabaseRequestConnection.use(request, logger) { connection =>
      request.databaseFormat match {
        case DatabaseHistoryFormat.RelationalFormat => new RelationalHistoryStorage(connection, logger).store(request)
        case DatabaseHistoryFormat.BlobFormat => new BlobHistoryStorage(connection, logger).store(request)
      }
    }
}
