package it.evadid.server.commandHandler.sql.sync

import it.evadid.distribution.commandTypes.SQLCommands.DeleteInDbRequest
import it.evadid.server.commandHandler.sql.{GenericSqlFunctionality, RichDatabaseConfig}
import it.evadid.sql.{DatabaseConfiguration, DatabaseHistoryFormat}
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.*

import java.sql.{Connection, PreparedStatement}
import java.time.LocalDateTime

object DeleteInDatabase {

  def handleRequest(request: DeleteInDbRequest, logger: Logger): SyncSuccess = {
    it.evadid.server.database.DatabaseRequestConnection.use(request, logger) { connection =>
      request.databaseFormat match {
        case DatabaseHistoryFormat.RelationalFormat =>
          new RelationalHistoryStorage(connection, logger).delete(request)
        case DatabaseHistoryFormat.BlobFormat =>
          new BlobHistoryStorage(connection, logger).delete(request)
      }
    }
  }
}
