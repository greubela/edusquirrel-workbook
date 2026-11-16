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
import upickle.default.*

/** Reads and deletes relational history. All inserts and upserts live in UpsertToDatabase. */
class RelationalHistoryStorage(connection: Connection, logger: Logger) extends RelationalHistorySql(connection, logger) {
  def fetch(request: FetchAllFromDbRequest): DbFetchResponse = transaction {
    val context = request.usageContext
    val limit = request.mayLimitToKey.map(_ => " AND variable_id=?").getOrElse("")
    val parameters = List(context.programId, context.scenarioId, context.userId) ++ request.mayLimitToKey.toList
    val contexts = query("SELECT id,variable_id FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=?" + limit, parameters)(row => row.getLong(1) -> row.getString(2))
    val histories = contexts.map { (sync, key) =>
      val states = query("SELECT id,timestamp,update_importance,serialized_data,format_id FROM answer_event WHERE sync_id=?", List(sync)) { row =>
        safe("answer " + row.getLong(1)) {
          row.getLong(1) -> answerJson(row) -> row.getString("format_id")
        }
      }.flatten.toMap
      
      val keyedGrades = query("SELECT * FROM grading_event WHERE sync_id=?", List(sync)).flatMap { row =>
        states.get(row.getLong("answer_id")).flatMap { case (_, event, formatId) =>
          safe("grading " + row.getLong("id")) {
            val result = readJson[GradingResult[ujson.Value, ujson.Value]]("GradingResult", gradeJson(row, event("value")))
            require(result.grader.key == row.getString("grader_key"), "Stored grader key mismatch")
            Some((result, LocalDateTime.parse(event("timestamp").str), formatId))
          }
        }
      }.groupBy(_._1.grader.key).values.map { results =>
        results.sortBy(_._2).map(_._1).reduce(mergeGrade)
      }
      val gradingHistory = writeJs(InteractionVariableGradingHistory.fromResults(keyedGrades))
      
      val scaffoldingHistory = query("SELECT * FROM scaffolding_event WHERE sync_id=?", List(sync)).flatMap { row =>
        states.get(row.getLong("answer_id")).flatMap { case (_, event, formatId) =>
          safe("scaffolding " + row.getLong("id")) {
            val result = readJson[ScaffoldingResult[ujson.Value, ujson.Value]]("ScaffoldingResult", scaffoldingJson(row, event("value")))
            Some((result, formatId))
          }
        }
      }.groupBy(_._1.forState).map { (state, results) =>
        state -> writeJs(results.map(_._1).reduce(_.merge(_)))
      }
      
      val history = ujson.Obj("historyStates" -> ujson.Obj("events" -> ujson.Arr.from(states.values.map(_._2))),
        "historyGrading" -> gradingHistory,
        "historyScaffolding" -> ujson.Obj("scaffoldingMap" -> ujson.Arr.from(scaffoldingHistory.map((state, value) => ujson.Arr(state, value)))))
      
      // Get format from first row, use default if not found
      val formatId = states.headOption.map(_._3).getOrElse("it.evadid.workbook.elements.interactionElements.basic.TextInteraction")
      val format = readFormat(formatId)
      val historyJson = ujson.write(history)
      context.toSyncContext(key) -> InteractionHistoryJson(format, historyJson)
    }.toMap
    DbFetchResponse(histories.map { case (ctx, ihj) => ctx -> ihj.historyJson })
  }

  /** Return event-based histories filtered to [starting, until]. */
  def fetchInRange(request: FetchEventsFromDbRequest): DbFetchResponse = transaction {
    val context = request.usageContext
    val starting = LocalDateTime.parse(request.starting)
    val until = LocalDateTime.parse(request.until)
    val keyFilter = if request.keys.isEmpty then "" else " AND variable_id IN (" + request.keys.map(_ => "?").mkString(",") + ")"
    val parameters = List(context.programId, context.scenarioId, context.userId) ++ request.keys.toList
    val contexts = query("SELECT id,variable_id FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=?"+keyFilter, parameters)(row => row.getLong(1) -> row.getString(2))
    val histories = contexts.map { (sync, key) =>
      val states = query("SELECT id,timestamp,update_importance,serialized_data,format_id FROM answer_event WHERE sync_id=? AND timestamp>=? AND timestamp<=?", List(sync, starting.format(timestampFormat), until.format(timestampFormat))) { row =>
        safe("answer " + row.getLong(1)) {
          row.getLong(1) -> answerJson(row) -> row.getString("format_id")
        }
      }.flatten.toMap
      
      val keyedGrades = query("SELECT * FROM grading_event WHERE sync_id=? AND timestamp>=? AND timestamp<=?", List(sync, starting.format(timestampFormat), until.format(timestampFormat))).flatMap { row =>
        states.get(row.getLong("answer_id")).flatMap { case (_, event, formatId) =>
          safe("grading " + row.getLong("id")) {
            val result = readJson[GradingResult[ujson.Value, ujson.Value]]("GradingResult", gradeJson(row, event("value")))
            require(result.grader.key == row.getString("grader_key"), "Stored grader key mismatch")
            Some((result, LocalDateTime.parse(event("timestamp").str), formatId))
          }
        }
      }.groupBy(_._1.grader.key).values.map { results =>
        results.sortBy(_._2).map(_._1).reduce(mergeGrade)
      }
      val gradingHistory = writeJs(InteractionVariableGradingHistory.fromResults(keyedGrades))
      
      val scaffoldingHistory = query("SELECT * FROM scaffolding_event WHERE sync_id=? AND timestamp>=? AND timestamp<=?", List(sync, starting.format(timestampFormat), until.format(timestampFormat))).flatMap { row =>
        states.get(row.getLong("answer_id")).flatMap { case (_, event, formatId) =>
          safe("scaffolding " + row.getLong("id")) {
            val result = readJson[ScaffoldingResult[ujson.Value, ujson.Value]]("ScaffoldingResult", scaffoldingJson(row, event("value")))
            Some((result, formatId))
          }
        }
      }.groupBy(_._1.forState).map { (state, results) =>
        state -> writeJs(results.map(_._1).reduce(_.merge(_)))
      }
      
      val history = ujson.Obj("historyStates" -> ujson.Obj("events" -> ujson.Arr.from(states.values.map(_._2))),
        "historyGrading" -> gradingHistory,
        "historyScaffolding" -> ujson.Obj("scaffoldingMap" -> ujson.Arr.from(scaffoldingHistory.map((state, value) => ujson.Arr(state, value)))))
      
      // Get format from first row, use default if not found
      val formatId = states.headOption.map(_._3).getOrElse("it.evadid.workbook.elements.interactionElements.basic.TextInteraction")
      val format = readFormat(formatId)
      val historyJson = ujson.write(history)
      context.toSyncContext(key) -> InteractionHistoryJson(format, historyJson)
    }.toMap
    DbFetchResponse(histories.map { case (ctx, ihj) => ctx -> ihj.historyJson })
  }

  def delete(request: DeleteInDbRequest): SyncSuccess = transaction {
    val context = request.usageContext
    val scenario = if request.allWorkbooks then "" else " AND scenario_id=?"
    val limit = request.limitToKey.map(_ => " AND variable_id=?").getOrElse("")
    val parameters = List(context.programId, context.userId) ++
      Option.unless(request.allWorkbooks)(context.scenarioId).toList ++ request.limitToKey.toList
    val removed = update("DELETE FROM sync_context_id WHERE program_id=? AND user_id=?" + scenario + limit, parameters*)
    SyncSuccess(0, 0, removed, LocalDateTime.now())
  }
  
  /** Read format from fully qualified class name. */
  private def readFormat(formatId: String): InteractionFormat[?, ?, ?] = {
    try {
      val clazz = Class.forName(formatId)
      val field = clazz.getField("MODULE$")
      field.get(null).asInstanceOf[InteractionFormat[?, ?, ?]]
    } catch {
      case e: Exception =>
        logger.warn(s"Failed to load format $formatId: ${e.getMessage}")
        throw e
    }
  }
}
