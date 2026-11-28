package it.evadid.server.database.history

import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.sync.SyncSuccess
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.history.InteractionHistory
import it.evadid.workbook.history.events.EventId
import java.sql.Connection

/** One JSON history per context row; concurrent merges are serialized by locking that row. */
class BlobHistoryStorage(connection: Connection, logger: Logger) extends HistorySql(connection, logger) {
  import BlobHistoryStorage.*

  protected val contextTable: HistorySql.ContextTableSql = sql

  def store(request: StoreToDbRequest): SyncSuccess[EventId] = transaction {
    val format = request.history.format
    val incoming = request.history.typedAs[format.type](format)
    val codec = InteractionFormat.codecsFor[format.type](format).historySerializer
    val empty = InteractionHistory.empty[format.type](format)
    val previous = lockedContextRow(request.syncContext, format, Seq(codec.serialize(empty))) { (row, sameFormat) =>
      if sameFormat then InteractionHistoryJson(format, row.getString("history_json")).typedAs[format.type](format) else empty
    }
    val merged = previous.mergeWith(incoming)
    statements.update(updateHistory, (Seq(format.name, codec.serialize(merged)) ++ HistorySql.identity(request.syncContext))*)
    SyncSuccess.immutable(incoming.events.ids, previous.events.ids, merged.events.ids)
  }

  def fetch(request: FetchEventsFromDbRequest): DbFetchResponse = transaction {
    readable(request, contextRows(request.usageContext, request.keys)(row => row.getString("format") -> row.getString("history_json"))) {
      (format, json) => InteractionHistoryJson(InteractionFormat.fromName(format), json)
    }
  }
}

object BlobHistoryStorage {
  private val sql = HistorySql.ContextTableSql(
    insertIfAbsent = "INSERT INTO blob_history (program_id,scenario_id,user_id,variable_id,format,history_json) " +
      "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
    lockRow = "SELECT format,history_json FROM blob_history " +
      "WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=? FOR UPDATE",
    selectAll = "SELECT variable_id,format,history_json FROM blob_history WHERE program_id=? AND scenario_id=? AND user_id=?",
    selectKeys = "SELECT variable_id,format,history_json FROM blob_history " +
      "WHERE program_id=? AND scenario_id=? AND user_id=? AND JSON_CONTAINS(?, JSON_QUOTE(variable_id))",
    lockDeleted = "SELECT scenario_id,variable_id FROM blob_history " +
      "WHERE program_id=? AND user_id=? AND (?=1 OR scenario_id=?) AND (?=0 OR variable_id=?) FOR UPDATE",
    delete = "DELETE FROM blob_history WHERE program_id=? AND user_id=? AND (?=1 OR scenario_id=?) AND (?=0 OR variable_id=?)")

  private val updateHistory =
    "UPDATE blob_history SET format=?, history_json=? WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=?"
}
