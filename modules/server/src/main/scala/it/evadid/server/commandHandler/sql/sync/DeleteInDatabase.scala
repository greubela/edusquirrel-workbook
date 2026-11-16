package it.evadid.server.commandHandler.sql.sync

import it.evadid.distribution.commandTypes.SQLCommands.DeleteInDbRequest
import it.evadid.server.commandHandler.sql.{GenericSqlFunctionality, RichDatabaseConfig}
import it.evadid.sql.DatabaseConfiguration
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.*

import java.sql.{Connection, PreparedStatement}
import java.time.LocalDateTime

case class DeleteInDatabase(
                             connection: Connection,
                             usageContext: UsageContext,
                             logger: Logger
                           ) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(logger)

}

object DeleteInDatabase {

  def handleRequest(request: DeleteInDbRequest, logger: Logger): SyncSuccess = {
    it.evadid.server.database.DatabaseRequestConnection.use(request, logger) { connection =>
      new RelationalHistoryStorage(connection, logger).delete(request)
    }
  }
}
