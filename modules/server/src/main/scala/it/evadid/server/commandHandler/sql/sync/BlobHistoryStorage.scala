package it.evadid.server.commandHandler.sql.sync

import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.{SyncSuccess, UsageContext}
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import java.sql.Connection
import java.time.LocalDateTime

/** Serializes concurrent merges by locking the context row inside the transaction. */
class BlobHistoryStorage(connection: Connection, logger: Logger) extends HistorySql(connection, logger) {
  private def identity(context: UsageContext, key: String): List[String] =
    List(context.programId, context.scenarioId, context.userId, key)

  def store(request: StoreToDbRequest): SyncSuccess = transaction {
    val incoming = request.history
    incoming.toTyped
    val keys = identity(request.usageContext, request.syncContext.keyForSerialisation)
    val inserted = update("INSERT INTO blob_history (program_id,scenario_id,user_id,variable_id,format,history_json) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
      (keys ++ List(incoming.format.name, incoming.historyJson))*)
    val existing = query("SELECT format,history_json FROM blob_history WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=? FOR UPDATE", keys) { row =>
      InteractionHistoryJson(InteractionFormat.fromName(row.getString(1)), row.getString(2))
    }.head
    val merged = existing.merge(incoming)
    update("UPDATE blob_history SET history_json=? WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=?",
      (List(merged.historyJson) ++ keys)*)
    SyncSuccess(0, if inserted == 1 then incoming.toTyped.size else merged.toTyped.size - existing.toTyped.size, 0, LocalDateTime.now())
  }

  private def histories(context: UsageContext, keys: Seq[String]): DbFetchResponse = {
    val filter = if keys.isEmpty then "" else " AND variable_id IN (" + keys.map(_ => "?").mkString(",") + ")"
    val entries = query("SELECT variable_id,format,history_json FROM blob_history WHERE program_id=? AND scenario_id=? AND user_id=?" + filter,
      List(context.programId, context.scenarioId, context.userId) ++ keys) { row =>
      val history = InteractionHistoryJson(InteractionFormat.fromName(row.getString(2)), row.getString(3))
      history.toTyped
      context.toSyncContext(row.getString(1)) -> history
    }
    DbFetchResponse(entries.toMap)
  }

  def fetch(request: FetchAllFromDbRequest): DbFetchResponse = transaction {
    histories(request.usageContext, request.mayLimitToKey.toList)
  }

  def fetchInRange(request: FetchEventsFromDbRequest): DbFetchResponse = transaction {
    val response = histories(request.usageContext, request.keys)
    response.copy(fetchedElements = response.fetchedElements.map((key, value) => key -> value.between(request.starting, request.until)))
  }

  def delete(request: DeleteInDbRequest): SyncSuccess = transaction {
    val context = request.usageContext
    val scenario = if request.allWorkbooks then "" else " AND scenario_id=?"
    val key = request.limitToKey.map(_ => " AND variable_id=?").getOrElse("")
    val removed = update("DELETE FROM blob_history WHERE program_id=? AND user_id=?" + scenario + key,
      (List(context.programId, context.userId) ++ Option.unless(request.allWorkbooks)(context.scenarioId).toList ++ request.limitToKey.toList)*)
    SyncSuccess(0, 0, removed, LocalDateTime.now())
  }
}
