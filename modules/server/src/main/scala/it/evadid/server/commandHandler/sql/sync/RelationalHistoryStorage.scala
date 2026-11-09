package it.evadid.server.commandHandler.sql.sync

import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.workbook.interaction.sync.{SyncContext, SyncSuccess}
import it.evadid.workbook.interaction.sync.model.HistoryJson
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.interaction.variable.InteractionVariableGradingHistory
import it.evadid.util.logging.Logger
import java.sql.{Connection, PreparedStatement, ResultSet, Types}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.util.{Try, Using}
import upickle.default.*

/** Converts current history values to relational rows. Numeric IDs never leave this class. */
class RelationalHistoryStorage(connection: Connection, logger: Logger) {
  private val timestampFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS")
  private def time(value: ujson.Value): String = LocalDateTime.parse(value.str).format(timestampFormat)
  private def restoredTime(value: String): ujson.Value = ujson.Str(LocalDateTime.parse(value).toString)

  private def prepared[A](sql: String, values: Seq[Any])(run: PreparedStatement => A): A =
    Using.resource(connection.prepareStatement(sql)) { statement =>
      values.zipWithIndex.foreach { (value, index) => value match {
        case null => statement.setNull(index + 1, Types.VARCHAR)
        case number: Long => statement.setLong(index + 1, number)
        case other => statement.setString(index + 1, other.toString)
      }}
      run(statement)
    }
  private def update(sql: String, values: Any*): Int = prepared(sql, values)(_.executeUpdate())
  private def query[A](sql: String, values: Seq[Any])(read: ResultSet => A): List[A] =
    prepared(sql, values) { statement =>
      Using.resource(statement.executeQuery())(rows => Iterator.continually(rows.next()).takeWhile(identity).map(_ => read(rows)).toList)
    }
  private def transaction[A](run: => A): A = {
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
  private def contextValues(context: SyncContext): List[String] =
    List(context.programId, context.scenarioId, context.userId, context.keyForSerialisation)
  private def ensureContext(context: SyncContext): Long = {
    update("INSERT INTO sync_context_id (program_id,scenario_id,user_id,variable_id) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE id=id", contextValues(context)*)
    query("SELECT id FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=? FOR UPDATE", contextValues(context))(_.getLong(1)).head
  }

  private def optionalData(result: ujson.Value, field: String): Any =
    result.obj.get(field).filter(_ != ujson.Null).map(ujson.write(_)).orNull
  private def readOptional(value: String): ujson.Value =
    Option(value).map(ujson.read(_)).getOrElse(ujson.Null)

  private def upsertAnswer(sync: Long, event: ujson.Value): Unit = {
    val timestamp = time(event("timestamp"))
    val value = event("value")
    val existing = query("SELECT id,serialized_data FROM answer_event WHERE sync_id=? AND timestamp=?", List(sync, timestamp)) { row =>
      row.getLong(1) -> ujson.read(row.getString(2))
    }.headOption
    existing.filter(_._2 != value).foreach { (id, _) =>
      update("DELETE FROM grading_event WHERE sync_id=? AND answer_id=?", sync, id)
      update("DELETE FROM scaffolding_event WHERE sync_id=? AND answer_id=?", sync, id)
    }
    update("INSERT INTO answer_event (sync_id,timestamp,update_importance,serialized_data) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE update_importance=VALUES(update_importance),serialized_data=VALUES(serialized_data)",
      sync, timestamp, event("updateImportance").str, ujson.write(value))
  }

  private def gradeJson(row: ResultSet, state: ujson.Value): ujson.Value = {
    val regular = ujson.read(row.getString("serialized_data"))
    ujson.Obj("grader" -> ujson.Str(row.getString("grader_definition")), "gradedState" -> state,
      "gradingStatus" -> regular("gradingStatus"), "feedbackInformation" -> regular.obj.getOrElse("feedbackInformation", ujson.Null),
      "additionalData" -> readOptional(row.getString("additional_data")), "gradingTime" -> restoredTime(row.getString("timestamp")))
  }
  private def scaffoldingJson(row: ResultSet, state: ujson.Value): ujson.Value =
    ujson.Obj("forState" -> state, "scaffoldingStatus" -> ujson.read(row.getString("serialized_data")),
      "scaffoldingEntity" -> ujson.read(row.getString("scaffolding_entity")),
      "additionalInformation" -> readOptional(row.getString("additional_data")), "scaffoldingTime" -> restoredTime(row.getString("timestamp")))

  def store(request: StoreToDbRequest): SyncSuccess = transaction {
    val history = HistoryJson.payload(request.historyJson)
    val sync = ensureContext(request.syncContext)
    history("historyStates")("events").arr.toList.sortBy(event => LocalDateTime.parse(event("timestamp").str)).foreach(upsertAnswer(sync, _))
    val answers = query("SELECT id,timestamp,serialized_data FROM answer_event WHERE sync_id=?", List(sync)) { row =>
      (row.getLong(1), LocalDateTime.parse(row.getString(2)), ujson.read(row.getString(3)))
    }
    def answerFor(state: ujson.Value): Long = answers.filter(_._3 == state).maxByOption(_._2)
      .map(_._1).getOrElse(throw IllegalArgumentException("Feedback has no matching saved answer in this sync context"))

    HistoryJson.entries(history("historyGrading")("gradingsMap")).foreach { (state, group) =>
      require(group("state") == state, "Grading state does not match its map key")
      HistoryJson.entries(group("graderResultMap")).foreach { (definition, incoming) =>
        require(incoming("gradedState") == state && incoming("grader") == definition, "Inconsistent grading result")
        val grader = read[WorkbookInteractionGrader[ujson.Value, ujson.Value]](definition)
        require(grader.key.startsWith(request.syncContext.keyForSerialisation + ":"), "Grader key belongs to another variable")
        val answer = answerFor(state)
        val previous = query("SELECT * FROM grading_event WHERE sync_id=? AND answer_id=? AND grader_key=?", List(sync, answer, grader.key))(gradeJson(_, state)).headOption
        val result = previous.map { previous =>
          val old = read[GradingResult[ujson.Value, ujson.Value]](previous)
          val current = read[GradingResult[ujson.Value, ujson.Value]](incoming)
          if old.grader == current.grader then writeJs(old.merge(current))
          else if old.gradingTime.isAfter(current.gradingTime) then previous else incoming
        }.getOrElse(incoming)
        val regular = ujson.Obj("gradingStatus" -> result("gradingStatus"),
          "feedbackInformation" -> result.obj.getOrElse("feedbackInformation", ujson.Null))
        update("INSERT INTO grading_event (sync_id,answer_id,grader_key,grader_definition,grading_entity,grading_status,timestamp,serialized_data,additional_data) VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE grader_definition=VALUES(grader_definition),grading_entity=VALUES(grading_entity),grading_status=VALUES(grading_status),timestamp=VALUES(timestamp),serialized_data=VALUES(serialized_data),additional_data=VALUES(additional_data)",
          sync, answer, grader.key, result("grader").str, write(grader.gradingEntity),
          read[GradingStatus](result("gradingStatus")).getClass.getSimpleName, time(result("gradingTime")),
          ujson.write(regular), optionalData(result, "additionalData"))
      }
    }
    HistoryJson.entries(history("historyScaffolding")("scaffoldingMap")).foreach { (state, incoming) =>
      require(incoming("forState") == state, "Scaffolding state does not match its map key")
      val answer = answerFor(state)
      val previous = query("SELECT * FROM scaffolding_event WHERE sync_id=? AND answer_id=?", List(sync, answer))(scaffoldingJson(_, state)).headOption
      val result = previous.map { old => writeJs(read[ScaffoldingResult[ujson.Value, ujson.Value]](old)
        .merge(read[ScaffoldingResult[ujson.Value, ujson.Value]](incoming))) }.getOrElse(incoming)
      update("INSERT INTO scaffolding_event (sync_id,answer_id,timestamp,scaffolding_entity,scaffolding_status,serialized_data,additional_data) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE timestamp=VALUES(timestamp),scaffolding_entity=VALUES(scaffolding_entity),scaffolding_status=VALUES(scaffolding_status),serialized_data=VALUES(serialized_data),additional_data=VALUES(additional_data)",
        sync, answer, time(result("scaffoldingTime")), ujson.write(result("scaffoldingEntity")),
        read[ScaffoldingStatus](result("scaffoldingStatus")).getClass.getSimpleName,
        ujson.write(result("scaffoldingStatus")), optionalData(result, "additionalInformation"))
    }
    val feedbackCount = HistoryJson.entries(history("historyGrading")("gradingsMap"))
      .map((_, group) => HistoryJson.entries(group("graderResultMap")).size).sum +
      HistoryJson.entries(history("historyScaffolding")("scaffoldingMap")).size
    SyncSuccess(0, history("historyStates")("events").arr.size + feedbackCount, 0, LocalDateTime.now())
  }

  private def safe[A](description: String)(read: => A): Option[A] = Try(read).fold(
    _ => { logger.logWarn("Discarded malformed relational history record: " + description); None }, Some(_))

  def fetch(request: FetchAllFromDbRequest): DbFetchResponse = transaction {
    val context = request.usageContext
    val limit = request.mayLimitToKey.map(_ => " AND variable_id=?").getOrElse("")
    val parameters = List(context.programId, context.scenarioId, context.userId) ++ request.mayLimitToKey.toList
    val contexts = query("SELECT id,variable_id FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=?" + limit, parameters)(row => row.getLong(1) -> row.getString(2))
    val histories = contexts.map { (sync, key) =>
      val states = query("SELECT id,timestamp,update_importance,serialized_data FROM answer_event WHERE sync_id=?", List(sync)) { row =>
        safe("answer " + row.getLong(1)) {
          row.getLong(1) -> ujson.Obj("value" -> ujson.read(row.getString(4)), "timestamp" -> restoredTime(row.getString(2)),
            "updateImportance" -> row.getString(3))
        }
      }.flatten.toMap
      val grades = query("SELECT * FROM grading_event WHERE sync_id=?", List(sync)) { row =>
        states.get(row.getLong("answer_id")).flatMap(event => safe("grading " + row.getLong("id")) {
          val result = read[GradingResult[ujson.Value, ujson.Value]](gradeJson(row, event("value")))
          require(result.grader.key == row.getString("grader_key"), "Stored grader key mismatch")
          result
        })
      }.flatten
      val scaffoldings = query("SELECT * FROM scaffolding_event WHERE sync_id=?", List(sync)) { row =>
        states.get(row.getLong("answer_id")).flatMap(event => safe("scaffolding " + row.getLong("id")) {
          read[ScaffoldingResult[ujson.Value, ujson.Value]](scaffoldingJson(row, event("value")))
        })
      }.flatten
      val gradingHistory = writeJs(InteractionVariableGradingHistory.fromResults(grades))
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
