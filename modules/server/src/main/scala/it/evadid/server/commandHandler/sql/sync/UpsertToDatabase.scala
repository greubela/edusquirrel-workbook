package it.evadid.server.commandHandler.sql.sync

import it.evadid.workbook.interaction.sync.model.HistoryJson
import it.evadid.distribution.commandTypes.SQLCommands.StoreToDbRequest
import it.evadid.server.commandHandler.sql.GenericSqlFunctionality
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.*

import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import upickle.default.*

import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime
import scala.util.Using

case class UpsertToDatabase(
                             connection: Connection,
                             usageContext: UsageContext,
                             logger: Logger
                           ) extends RelationalHistorySql(connection, logger) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(logger)
  private lazy val delete: DeleteInDatabase = new DeleteInDatabase(connection, usageContext, logger)

  private def contextValues(context: SyncContext): List[String] =
    List(context.programId, context.scenarioId, context.userId, context.keyForSerialisation)
  private def ensureContext(context: SyncContext): Long = {
    update("INSERT INTO sync_context_id (program_id,scenario_id,user_id,variable_id) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE id=id", contextValues(context)*)
    query("SELECT id FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=? FOR UPDATE", contextValues(context))(_.getLong(1)).head
  }

  private def optionalData(result: ujson.Value, field: String): Any =
    result.obj.get(field).filter(_ != ujson.Null).map(ujson.write(_)).orNull

  private def upsertAnswer(sync: Long, event: ujson.Value): Unit = {
    val timestamp = time(event("timestamp"))
    val value = event("value")
    val existing = query("SELECT id,serialized_data FROM answer_event WHERE sync_id=? AND timestamp=?", List(sync, timestamp)) { row =>
      row.getLong(1) -> parseJson("answer_event.serialized_data", row.getString(2))
    }.headOption
    existing.filter(_._2 != value).foreach { (id, _) =>
      update("DELETE FROM grading_event WHERE sync_id=? AND answer_id=?", sync, id)
      update("DELETE FROM scaffolding_event WHERE sync_id=? AND answer_id=?", sync, id)
    }
    update("INSERT INTO answer_event (sync_id,timestamp,update_importance,serialized_data) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE update_importance=VALUES(update_importance),serialized_data=VALUES(serialized_data)",
      sync, timestamp, event("updateImportance").str, ujson.write(value))
  }

  /** Stores V2 answers and feedback atomically, preserving IDs on retries. */
  def upsertIntoRelationalTables(request: StoreToDbRequest): SyncSuccess = transaction {
    val history = request.history.toHistoryPayload
    val sync = ensureContext(request.syncContext)
    history.historyStates.events.values.toList.sortBy(_.timestamp).foreach(upsertAnswer(sync, _))
    val answers = query("SELECT id,timestamp,update_importance,serialized_data FROM answer_event WHERE sync_id=?", List(sync)) { row =>
      safe("answer " + row.getLong("id")) {
        val event = answerJson(row)
        (row.getLong("id"), LocalDateTime.parse(event("timestamp").str), event("value"))
      }
    }.flatten
    def answerFor(state: ujson.Value): Long = answers.filter(_._3 == state).maxByOption(_._2)
      .map(_._1).getOrElse(throw IllegalArgumentException("Feedback has no matching saved answer in this sync context"))

    history.historyGrading.gradingsMap.foreach { case (state, group) =>
      require(group.state == state, "Grading state does not match its map key")
      group.graderResultMap.foreach { case (definition, incoming) =>
        require(incoming.gradedState == state && incoming.grader == definition, "Inconsistent grading result")
        val grader = incoming.grader
        require(grader.key.nonEmpty, "Grader identity must not be empty")
        val answer = answerFor(state)
        upsertGrading(sync, answer, state, grader, incoming)
      }
    }
    history.historyScaffolding.scaffoldingMap.foreach { case (state, incoming) =>
      require(incoming.forState == state, "Scaffolding state does not match its map key")
      val answer = answerFor(state)
      upsertScaffolding(sync, answer, state, incoming)
    }
    val feedbackCount = history.historyGrading.gradingsMap.map { case (_, group) =>
      group.graderResultMap.size
    }.sum + history.historyScaffolding.scaffoldingMap.size
    SyncSuccess(0, history.historyStates.events.values.size + feedbackCount, 0, LocalDateTime.now())
  }

  private def upsertGrading(sync: Long, answer: Long, state: ujson.Value,
                            grader: WorkbookInteractionGrader[ujson.Value, ujson.Value], incoming: ujson.Value): Unit = {
    val previous = query("SELECT * FROM grading_event WHERE sync_id=? AND answer_id=? AND grader_key=?", List(sync, answer, grader.key)) { row =>
      safe("previous grading " + row.getLong("id")) {
        writeJs(readJson[GradingResult[ujson.Value, ujson.Value]]("GradingResult", gradeJson(row, state)))
      }
    }.flatten.headOption
    val result = previous.map { previous =>
      val old = readJson[GradingResult[ujson.Value, ujson.Value]]("GradingResult", previous)
      val current = readJson[GradingResult[ujson.Value, ujson.Value]]("GradingResult", incoming)
      writeJs(mergeGrade(old, current))
    }.getOrElse(incoming)
    val regular = ujson.Obj("gradingStatus" -> result("gradingStatus"),
      "feedbackInformation" -> result.obj.getOrElse("feedbackInformation", ujson.Null))
    val storedGrader = readJson[WorkbookInteractionGrader[ujson.Value, ujson.Value]]("WorkbookInteractionGrader", result("grader"))
    update("INSERT INTO grading_event (sync_id,answer_id,grader_key,grader_definition,grading_entity,grading_status,timestamp,serialized_data,additional_data) VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE grader_definition=VALUES(grader_definition),grading_entity=VALUES(grading_entity),grading_status=VALUES(grading_status),timestamp=VALUES(timestamp),serialized_data=VALUES(serialized_data),additional_data=VALUES(additional_data)",
      sync, answer, grader.key, result("grader").str, write(storedGrader.gradingEntity),
      readJson[GradingStatus]("GradingStatus", result("gradingStatus")).getClass.getSimpleName, time(result("gradingTime")),
      ujson.write(regular), optionalData(result, "additionalData"))
  }

  private def upsertScaffolding(sync: Long, answer: Long, state: ujson.Value, incoming: ujson.Value): Unit = {
    val previous = query("SELECT * FROM scaffolding_event WHERE sync_id=? AND answer_id=?", List(sync, answer)) { row =>
      safe("previous scaffolding " + row.getLong("id")) {
        writeJs(readJson[ScaffoldingResult[ujson.Value, ujson.Value]]("ScaffoldingResult", scaffoldingJson(row, state)))
      }
    }.flatten.headOption
    val result = previous.map { old => writeJs(readJson[ScaffoldingResult[ujson.Value, ujson.Value]]("ScaffoldingResult", old)
      .merge(readJson[ScaffoldingResult[ujson.Value, ujson.Value]]("ScaffoldingResult", incoming))) }.getOrElse(incoming)
    update("INSERT INTO scaffolding_event (sync_id,answer_id,timestamp,scaffolding_entity,scaffolding_status,serialized_data,additional_data) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE timestamp=VALUES(timestamp),scaffolding_entity=VALUES(scaffolding_entity),scaffolding_status=VALUES(scaffolding_status),serialized_data=VALUES(serialized_data),additional_data=VALUES(additional_data)",
      sync, answer, time(result("scaffoldingTime")), ujson.write(result("scaffoldingEntity")),
      readJson[ScaffoldingStatus]("ScaffoldingStatus", result("scaffoldingStatus")).getClass.getSimpleName,
      ujson.write(result("scaffoldingStatus")), optionalData(result, "additionalInformation"))
  }

  def findMaxTimestamp(history: InteractionHistoryJson[?, ?, ?]): Option[LocalDateTime] = history.lastTimestamp
}

object UpsertToDatabase {

  def handleRequest(request: StoreToDbRequest, logger: Logger): SyncSuccess =
    if (request.history.lastTimestamp.isEmpty) SyncSuccess(0, 0, 0, LocalDateTime.now()) else {
      it.evadid.server.database.DatabaseRequestConnection.use(request, logger) { connection =>
        val control = UpsertToDatabase(connection, request.usageContext, logger)
        control.upsertIntoRelationalTables(request)
      }
    }
}
