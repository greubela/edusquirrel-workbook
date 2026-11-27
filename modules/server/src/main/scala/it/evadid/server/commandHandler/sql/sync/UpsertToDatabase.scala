package it.evadid.server.commandHandler.sql.sync

import it.evadid.distribution.commandTypes.SQLCommands.{BulkStoreToDbRequest, StoreToDbRequest}
import it.evadid.server.database.DatabaseRequestConnection
import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.SyncSuccess
import it.evadid.workbook.interaction.variable.events.EventId

object UpsertToDatabase {
  def handleRequest(request: StoreToDbRequest, logger: Logger): SyncSuccess[EventId] =
    DatabaseRequestConnection.use(request, logger) { connection =>
      request.databaseFormat match {
        case DatabaseHistoryFormat.RelationalFormat => new RelationalHistoryStorage(connection, logger).store(request)
        case DatabaseHistoryFormat.BlobFormat => new BlobHistoryStorage(connection, logger).store(request)
      }
    }
  def handleBulkRequest(request: BulkStoreToDbRequest, logger: Logger): SyncSuccess[EventId] =
    DatabaseRequestConnection.use(request, logger) { connection =>
      request.databaseFormat match {
        case DatabaseHistoryFormat.RelationalFormat => new RelationalHistoryStorage(connection, logger).storeAll(request)
        case DatabaseHistoryFormat.BlobFormat => new BlobHistoryStorage(connection, logger).storeAll(request)
      }
    }

}
