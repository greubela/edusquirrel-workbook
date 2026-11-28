package it.evadid.server.commandHandler.sql.sync

import it.evadid.util.logging.Logger
import it.evadid.distribution.commandTypes.SQLCommands.{BulkStoreToDbRequest, DbFetchResponse, DbRequest, DeleteInDbRequest, FetchEventsFromDbRequest, StoreToDbRequest}
import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.workbook.sync.{SyncContext, SyncSuccess}
import it.evadid.workbook.history.events.EventId
import it.evadid.server.database.{DatabaseRequestConnection, SqlStatements}
import java.sql.Connection

/** Shared statements and transaction ownership for both history layouts. */
abstract class HistorySql(connection: Connection, logger: Logger) {
  protected final val statements = SqlStatements(connection, logger)

  def store(request: StoreToDbRequest): SyncSuccess[EventId]
  def fetch(request: FetchEventsFromDbRequest): DbFetchResponse
  def delete(request: DeleteInDbRequest): SyncSuccess[SyncContext]

  /** One transaction and deterministic lock order across the entire network batch. */
  final def storeAll(request: BulkStoreToDbRequest): SyncSuccess[EventId] = transaction {
    request.entries.sortBy(entry => (entry.syncContext.programId, entry.syncContext.scenarioId,
      entry.syncContext.userId, entry.syncContext.keyForSerialisation))
      .foldLeft(SyncSuccess.emptyNow[EventId]())((result, entry) => result.combine(store(entry)))
  }

  /** Deletes the request's history rows from a table keyed by context, reporting the removed contexts. */
  protected final def deleteMatching(table: String, request: DeleteInDbRequest): SyncSuccess[SyncContext] = transaction {
    val context = request.usageContext
    val filter = "WHERE program_id=? AND user_id=?" + (if request.allWorkbooks then "" else " AND scenario_id=?") +
      request.limitToKey.map(_ => " AND variable_id=?").getOrElse("")
    val parameters = List(context.programId, context.userId) ++ Option.unless(request.allWorkbooks)(context.scenarioId).toList ++ request.limitToKey.toList
    val removed = statements.query(s"SELECT scenario_id,variable_id FROM $table $filter FOR UPDATE", parameters*)(row =>
      SyncContext(context.programId, row.getString(1), context.userId, row.getString(2))).toSet
    statements.update(s"DELETE FROM $table $filter", parameters*)
    SyncSuccess.removed(removed)
  }

  protected def transaction[A](run: => A): A = {
    val own = connection.getAutoCommit
    val isolation = connection.getTransactionIsolation
    if own then {
      connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ)
      connection.setAutoCommit(false)
    }
    try {
      val result = run
      if own then connection.commit()
      result
    } catch {
      case scala.util.control.NonFatal(error) =>
        if own then connection.rollback()
        throw error
    } finally if own then {
      connection.setAutoCommit(true)
      connection.setTransactionIsolation(isolation)
    }
  }
}

object HistorySql {
  /** Opens the request's validated connection and runs `operation` on the storage for its history layout. */
  def forRequest[A](request: DbRequest, logger: Logger)(operation: HistorySql => A): A =
    DatabaseRequestConnection.use(request, logger) { connection =>
      operation(request.databaseFormat match {
        case DatabaseHistoryFormat.RelationalFormat => RelationalHistoryStorage(connection, logger)
        case DatabaseHistoryFormat.BlobFormat => BlobHistoryStorage(connection, logger)
      })
    }
}
