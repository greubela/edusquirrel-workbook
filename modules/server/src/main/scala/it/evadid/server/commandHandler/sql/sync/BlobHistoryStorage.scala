package it.evadid.server.commandHandler.sql.sync

import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.{SyncSuccess, UsageContext}
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import java.sql.Connection
import java.time.LocalDateTime

/** Serializes concurrent merges by locking the context row inside the transaction. */
class BlobHistoryStorage(connection: Connection, logger: Logger) extends HistorySql(connection, logger) {
  private def identity(context: UsageContext, key: String): Seq[String] =
    List(context.programId, context.scenarioId, context.userId, key)

  def store(request: StoreToDbRequest): SyncSuccess = transaction {
    val incoming = request.history
    val format = incoming.format
    val newHistory = incoming.typedAs[format.type](format)
    val codec = InteractionFormat.codecsFor[format.type](format).historySerializer
    val emptyJson = codec.serialize(InteractionVariableHistory.empty[format.type])
    val keys = identity(request.usageContext, request.syncContext.keyForSerialisation)
    update("INSERT INTO blob_history (program_id,scenario_id,user_id,variable_id,format,history_json) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
      (keys ++ List(format.name, emptyJson))*)
    val existing = query("SELECT format,history_json FROM blob_history WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=? FOR UPDATE", keys) { row =>
      InteractionHistoryJson(InteractionFormat.fromName(row.getString(1)), row.getString(2))
    }.head
    val previous = existing.typedAs[format.type](format)
    val merged = previous.mergeWith(newHistory)
    update("UPDATE blob_history SET history_json=? WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=?",
      (List(codec.serialize(merged)) ++ keys)*)
    SyncSuccess(0, merged.size - previous.size, 0, LocalDateTime.now())
  }

  private def histories(context: UsageContext, keys: Seq[String]): DbFetchResponse = {
    val filter = if keys.isEmpty then "" else " AND variable_id IN (" + keys.map(_ => "?").mkString(",") + ")"
    val entries = query("SELECT variable_id,format,history_json FROM blob_history WHERE program_id=? AND scenario_id=? AND user_id=?" + filter,
      List(context.programId, context.scenarioId, context.userId) ++ keys) { row =>
      context.toSyncContext(row.getString(1)) -> InteractionHistoryJson(InteractionFormat.fromName(row.getString(2)), row.getString(3))
    }
    DbFetchResponse(entries.toMap)
  }

  def fetch(request: FetchEventsFromDbRequest): DbFetchResponse = transaction {
    DbFetchResponse.readable(histories(request.usageContext, request.keys).fetchedElements, request.strategy, request.starting,
      request.until, (key, error) => logger.logExceptionWarn(s"Discarded the unreadable history of ${key.keyForSerialisation}", error))
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
