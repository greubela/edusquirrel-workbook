package it.evadid.server.commandHandler.sql.sync

import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.workbook.interaction.sync.SyncSuccess
import it.evadid.workbook.abstractions.grading.GradingResult
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.interaction.variable.InteractionVariableGradingHistory
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
      val states = query("SELECT id,timestamp,update_importance,serialized_data FROM answer_event WHERE sync_id=?", List(sync)) { row =>
        safe("answer " + row.getLong(1)) {
          row.getLong(1) -> answerJson(row)
        }
      }.flatten.toMap
      val grades = query("SELECT * FROM grading_event WHERE sync_id=?", List(sync)) { row =>
        states.get(row.getLong("answer_id")).flatMap(event => safe("grading " + row.getLong("id")) {
          val result = read[GradingResult[ujson.Value, ujson.Value]](gradeJson(row, event("value")))
          require(result.grader.key == row.getString("grader_key"), "Stored grader key mismatch")
          result -> LocalDateTime.parse(event("timestamp").str)
        })
      }.flatten
      val scaffoldings = query("SELECT * FROM scaffolding_event WHERE sync_id=?", List(sync)) { row =>
        states.get(row.getLong("answer_id")).flatMap(event => safe("scaffolding " + row.getLong("id")) {
          read[ScaffoldingResult[ujson.Value, ujson.Value]](scaffoldingJson(row, event("value")))
        })
      }.flatten
      val keyedGrades = grades.groupBy((result, _) => (result.gradedState, result.grader.key)).values.map { results =>
        results.sortBy((result, answerTime) => (result.gradingTime, answerTime)).map(_._1).reduce(mergeGrade)
      }
      val gradingHistory = writeJs(InteractionVariableGradingHistory.fromResults(keyedGrades))
      val scaffoldingHistory = scaffoldings.groupBy(_.forState).map { (state, results) =>
        state -> writeJs(results.reduce(_.merge(_)))
      }
      val history = ujson.Obj("historyStates" -> ujson.Obj("events" -> ujson.Arr.from(states.values)),
        "historyGrading" -> gradingHistory,
        "historyScaffolding" -> ujson.Obj("scaffoldingMap" -> ujson.Arr.from(scaffoldingHistory.map((state, value) => ujson.Arr(state, value)))))
      context.toSyncContext(key) -> ujson.write(history)
    }.toMap
    DbFetchResponse(histories)
  }

  def delete(request: DeleteInDbRequest): SyncSuccess = transaction {
    val context = request.usageContext
    val limit = request.limitToKey.map(_ => " AND variable_id=?").getOrElse("")
    val removed = update("DELETE FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=?" + limit,
      (List(context.programId, context.scenarioId, context.userId) ++ request.limitToKey.toList)*)
    SyncSuccess(0, 0, removed, LocalDateTime.now())
  }
}
