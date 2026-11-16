package it.evadid.server.commandHandler.sql.sync

import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.distribution.commandTypes.SQLCommands.{DbFetchResponse, FetchAllFromDbRequest, FetchEventsFromDbRequest}
import it.evadid.server.commandHandler.sql.{GenericSqlFunctionality, RichDatabaseConfig}
import it.evadid.sql.DatabaseConfiguration
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.{SyncContext, UsageContext}

import java.sql.*

object FetchFromDatabase {

  private def union(list: List[String]): String = InteractionHistoryJson.merge(InteractionFormat.fromReadWriters[Unit, Unit, Unit], list)

  def handleRequest(request: FetchAllFromDbRequest, logger: Logger): DbFetchResponse = {
    it.evadid.server.database.DatabaseRequestConnection.use(request, logger) { connection =>
      request.databaseFormat match {
        case DatabaseHistoryFormat.RelationalEvents =>
          new RelationalHistoryStorage(connection, logger).fetch(request)
        case DatabaseHistoryFormat.SingleBlob =>
          new SingleBlobHistoryStorage(connection, logger).fetch(request)
      }
    }
  }

  def handleRangeRequest(request: FetchEventsFromDbRequest, logger: Logger): DbFetchResponse = {
    it.evadid.server.database.DatabaseRequestConnection.use(request, logger) { connection =>
      request.databaseFormat match {
        case DatabaseHistoryFormat.RelationalEvents =>
          new RelationalHistoryStorage(connection, logger).fetchInRange(request)
        case DatabaseHistoryFormat.SingleBlob =>
          new SingleBlobHistoryStorage(connection, logger).fetchInRange(request)
      }
    }
  }

}
