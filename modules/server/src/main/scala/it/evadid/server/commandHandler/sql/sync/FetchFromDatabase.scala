package it.evadid.server.commandHandler.sql.sync

import it.evadid.workbook.interaction.sync.model.HistoryJson
import it.evadid.distribution.commandTypes.SQLCommands.{DbFetchResponse, FetchAllFromDbRequest, FetchEventsFromDbRequest}
import it.evadid.server.commandHandler.sql.{GenericSqlFunctionality, RichDatabaseConfig}
import it.evadid.sql.DatabaseConfiguration
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.{SyncContext, UsageContext}

import java.sql.*

case class FetchFromDatabase(
                              connection: Connection,
                              usageContext: UsageContext,
                              logger: Logger
                            ) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(logger)

}

object FetchFromDatabase {

  private def union(list: List[String]): String = HistoryJson.merge(list)

  def handleRequest(request: FetchAllFromDbRequest, logger: Logger): DbFetchResponse = {
    it.evadid.server.database.DatabaseRequestConnection.use(request, logger) { connection =>
      new RelationalHistoryStorage(connection, logger).fetch(request)
    }
  }

  def handleRangeRequest(request: FetchEventsFromDbRequest, logger: Logger): DbFetchResponse = {
    it.evadid.server.database.DatabaseRequestConnection.use(request, logger) { connection =>
      new RelationalHistoryStorage(connection, logger).fetchInRange(request)
    }
  }

}
