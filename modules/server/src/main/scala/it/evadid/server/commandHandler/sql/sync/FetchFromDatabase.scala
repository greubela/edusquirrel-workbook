package it.evadid.server.commandHandler.sql.sync

import it.evadid.distribution.commandTypes.SQLCommands.{DbFetchResponse, FetchEventsFromDbRequest}
import it.evadid.server.database.DatabaseRequestConnection
import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.util.logging.Logger

object FetchFromDatabase {
  def handleRequest(request: FetchEventsFromDbRequest, logger: Logger): DbFetchResponse =
    DatabaseRequestConnection.use(request, logger) { connection =>
      request.databaseFormat match {
        case DatabaseHistoryFormat.RelationalFormat => new RelationalHistoryStorage(connection, logger).fetch(request)
        case DatabaseHistoryFormat.BlobFormat => new BlobHistoryStorage(connection, logger).fetch(request)
      }
    }
}
