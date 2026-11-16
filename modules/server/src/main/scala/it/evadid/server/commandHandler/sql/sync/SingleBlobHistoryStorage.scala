package it.evadid.server.commandHandler.sql.sync

import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.workbook.interaction.sync.SyncSuccess
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.abstractions.grading.GradingResult
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.interaction.variable.InteractionVariableGradingHistory
import it.evadid.core.util.io.serializer.InteractionFormat
import it.evadid.util.logging.Logger
import java.sql.Connection
import java.time.LocalDateTime

/** Reads and deletes single-blob history. All inserts and upserts live in UpsertToDatabase. */
class SingleBlobHistoryStorage(connection: Connection, logger: Logger) {
  def fetch(request: FetchAllFromDbRequest): DbFetchResponse = transaction {
    val context = request.usageContext
    val limit = request.mayLimitToKey.map(_ => " AND variable_id=?").getOrElse("")
    val parameters = List(context.programId, context.scenarioId, context.userId) ++ request.mayLimitToKey.toList
    val rows = query("SELECT variable_id, format, history_json FROM single_blob_history WHERE program_id=? AND scenario_id=? AND user_id=?" + limit, parameters) { row =>
      val variableId = row.getString("variable_id")
      val formatJson = row.getString("format")
      val historyJson = row.getString("history_json")
      variableId -> formatJson -> historyJson
    }
    
    val histories = rows.map { case (variableId, formatJson, historyJson) =>
      val syncContext = context.toSyncContext(variableId)
      val format = readFormat(formatJson)
      syncContext -> InteractionHistoryJson(format, historyJson)
    }.toMap
    DbFetchResponse(histories)
  }

  /** Return event-based histories filtered to [starting, until]. */
  def fetchInRange(request: FetchEventsFromDbRequest): DbFetchResponse = transaction {
    val context = request.usageContext
    val starting = LocalDateTime.parse(request.starting)
    val until = LocalDateTime.parse(request.until)
    val keyFilter = if request.keys.isEmpty then "" else " AND variable_id IN (" + request.keys.map(_ => "?").mkString(",") + ")"
    val parameters = List(context.programId, context.scenarioId, context.userId) ++ request.keys.toList
    val rows = query("SELECT variable_id, format, history_json FROM single_blob_history WHERE program_id=? AND scenario_id=? AND user_id=?"+keyFilter, parameters) { row =>
      val variableId = row.getString("variable_id")
      val formatJson = row.getString("format")
      val historyJson = row.getString("history_json")
      variableId -> formatJson -> historyJson
    }
    
    val histories = rows.map { case (variableId, formatJson, historyJson) =>
      val syncContext = context.toSyncContext(variableId)
      val format = readFormat(formatJson)
      syncContext -> InteractionHistoryJson(format, historyJson)
    }.toMap
    DbFetchResponse(histories)
  }

  def delete(request: DeleteInDbRequest): SyncSuccess = transaction {
    val context = request.usageContext
    val scenario = if request.allWorkbooks then "" else " AND scenario_id=?"
    val limit = request.limitToKey.map(_ => " AND variable_id=?").getOrElse("")
    val parameters = List(context.programId, context.userId) ++
      Option.unless(request.allWorkbooks)(context.scenarioId).toList ++ request.limitToKey.toList
    val removed = update("DELETE FROM single_blob_history WHERE program_id=? AND user_id=?" + scenario + limit, parameters*)
    SyncSuccess(0, 0, removed, LocalDateTime.now())
  }
  
  /** Read format from class name (using InteractionFormat.fromClassName). */
  private def readFormat(formatClassName: String): InteractionFormat[?, ?, ?] = {
    InteractionFormat.fromClassName(formatClassName)
  }
  
  private def transaction[A](f: => A): A = {
    connection.setAutoCommit(false)
    try {
      val result = f
      connection.commit()
      result
    } catch {
      case e: Exception =>
        connection.rollback()
        throw e
    } finally {
      connection.setAutoCommit(true)
    }
  }
  
  private def query[A](sql: String, params: List[String])(f: java.sql.ResultSet => A): List[A] = {
    Using.resource(connection.prepareStatement(sql)) { stmt =>
      params.zipWithIndex.foreach { case (param, idx) =>
        stmt.setString(idx + 1, param)
      }
      Using.resource(stmt.executeQuery()) { rs =>
        List.newBuilder[A].++=(() => rs).toList
      }
    }
  }
  
  private def query[A, B](sql: String, params: List[String])(f: java.sql.ResultSet => (A, B)): List[(A, B)] = {
    Using.resource(connection.prepareStatement(sql)) { stmt =>
      params.zipWithIndex.foreach { case (param, idx) =>
        stmt.setString(idx + 1, param)
      }
      Using.resource(stmt.executeQuery()) { rs =>
        List.newBuilder[(A, B)].++=(() => rs).toList
      }
    }
  }
  
  private def update(sql: String, params: String*): Int = {
    Using.resource(connection.prepareStatement(sql)) { stmt =>
      params.zipWithIndex.foreach { case (param, idx) =>
        stmt.setString(idx + 1, param)
      }
      stmt.executeUpdate()
    }
  }
}
