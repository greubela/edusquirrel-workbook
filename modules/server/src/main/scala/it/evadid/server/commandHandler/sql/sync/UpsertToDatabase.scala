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

case class UpsertToDatabase(
                             connection: Connection,
                             usageContext: UsageContext,
                             logger: Logger
                           ) extends RelationalHistorySql(connection, logger) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(connection, logger)
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
      row.getLong(1) -> ujson.read(row.getString(2))
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
        require(grader.key.nonEmpty, "Grader identity must not be empty")
        val answer = answerFor(state)
        val previous = query("SELECT * FROM grading_event WHERE sync_id=? AND answer_id=? AND grader_key=?", List(sync, answer, grader.key)) { row =>
          safe("previous grading " + row.getLong("id")) {
            writeJs(read[GradingResult[ujson.Value, ujson.Value]](gradeJson(row, state)))
          }
        }.flatten.headOption
        val result = previous.map { previous =>
          val old = read[GradingResult[ujson.Value, ujson.Value]](previous)
          val current = read[GradingResult[ujson.Value, ujson.Value]](incoming)
          writeJs(mergeGrade(old, current))
        }.getOrElse(incoming)
        val regular = ujson.Obj("gradingStatus" -> result("gradingStatus"),
          "feedbackInformation" -> result.obj.getOrElse("feedbackInformation", ujson.Null))
        val storedGrader = read[WorkbookInteractionGrader[ujson.Value, ujson.Value]](result("grader"))
        update("INSERT INTO grading_event (sync_id,answer_id,grader_key,grader_definition,grading_entity,grading_status,timestamp,serialized_data,additional_data) VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE grader_definition=VALUES(grader_definition),grading_entity=VALUES(grading_entity),grading_status=VALUES(grading_status),timestamp=VALUES(timestamp),serialized_data=VALUES(serialized_data),additional_data=VALUES(additional_data)",
          sync, answer, grader.key, result("grader").str, write(storedGrader.gradingEntity),
          read[GradingStatus](result("gradingStatus")).getClass.getSimpleName, time(result("gradingTime")),
          ujson.write(regular), optionalData(result, "additionalData"))
      }
    }
    HistoryJson.entries(history("historyScaffolding")("scaffoldingMap")).foreach { (state, incoming) =>
      require(incoming("forState") == state, "Scaffolding state does not match its map key")
      val answer = answerFor(state)
      val previous = query("SELECT * FROM scaffolding_event WHERE sync_id=? AND answer_id=?", List(sync, answer)) { row =>
        safe("previous scaffolding " + row.getLong("id")) {
          writeJs(read[ScaffoldingResult[ujson.Value, ujson.Value]](scaffoldingJson(row, state)))
        }
      }.flatten.headOption
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

  def findMaxTimestamp(history: String): Option[LocalDateTime] = HistoryJson.lastTimestamp(history)

  def upsertIntoTableWithKeys(request: StoreToDbRequest): SyncSuccess =
    if (HistoryJson.lastTimestamp(request.historyJson).isEmpty) SyncSuccess(0, 0, 0, LocalDateTime.now()) else {
      val sql =
        s"""
           |INSERT INTO `eventsWithKey` (`programid`, `scenarioid`, `userid`, `timestamp`, `serializeddata`, `eventkey`)
           |VALUES (?, ?, ?, ?, ?, ?)
           |ON DUPLICATE KEY UPDATE
           |  `timestamp` = VALUES(`timestamp`),
           |  `serializeddata` = VALUES(`serializeddata`)
           |""".stripMargin


      val stmt = connection.prepareStatement(sql)
      stmt.setString(1, request.syncContext.programId)
      stmt.setString(2, request.syncContext.scenarioId)
      stmt.setString(3, request.syncContext.userId)
      stmt.setTimestamp(4, Timestamp.valueOf(HistoryJson.lastTimestamp(request.historyJson).get))
      stmt.setString(5, HistoryJson.stored(request.syncContext.keyForSerialisation, request.historyJson))
      stmt.setString(6, request.syncContext.keyForSerialisation)

      val res = generic.executeUpdate(stmt)
      println(s"Upserted $res event into database")
      SyncSuccess(0, res, 0, LocalDateTime.now())
    }

  def upsertIntoTableWithoutKeys(request: StoreToDbRequest, tableName: String = "events"): SyncSuccess = {
    val key = request.syncContext.keyForSerialisation
    delete.deleteByKeyInDatabaseWithoutKey(key, tableName)
    insertIntoTableWithoutKeys(request, tableName)
  }

  def insertIntoTableWithoutKeys(request: StoreToDbRequest, tableName: String = "events"): SyncSuccess = {
    if (HistoryJson.lastTimestamp(request.historyJson).isEmpty) SyncSuccess(0, 0, 0, LocalDateTime.now()) else {
      val sql =
        s"""
           |INSERT INTO `$tableName` (`programid`, `scenarioid`, `userid`, `timestamp`, `serializeddata`)
           |VALUES (?, ?, ?, ?, ?)
           |ON DUPLICATE KEY UPDATE
           |  `timestamp` = VALUES(`timestamp`),
           |  `serializeddata` = VALUES(`serializeddata`)
           |""".stripMargin

      val stmt = connection.prepareStatement(sql)

      stmt.setString(1, request.syncContext.programId)
      stmt.setString(2, request.syncContext.scenarioId)
      stmt.setString(3, request.syncContext.userId)
      stmt.setTimestamp(4, Timestamp.valueOf(HistoryJson.lastTimestamp(request.historyJson).get))
      stmt.setString(5, HistoryJson.stored(request.syncContext.keyForSerialisation, request.historyJson))

      val res = generic.executeUpdate(stmt)
      println(s"Inserted $res event into database")
      SyncSuccess(res, 0, 0, LocalDateTime.now())
    }

  }
}

object UpsertToDatabase {

  def handleRequest(request: StoreToDbRequest, logger: Logger): SyncSuccess =
    if (HistoryJson.lastTimestamp(request.historyJson).isEmpty) SyncSuccess(0, 0, 0, LocalDateTime.now()) else {
      it.evadid.server.database.DatabaseRequestConnection.use(request) { connection =>
        val control = UpsertToDatabase(connection, request.usageContext, logger)
        request.databaseFormat match {
          case it.evadid.sql.DatabaseHistoryFormat.RelationalEvents => control.upsertIntoRelationalTables(request)
          case format: it.evadid.sql.DatabaseHistoryFormat.Snapshot =>
            if format.hasKeyTable then control.upsertIntoTableWithKeys(request)
            else control.upsertIntoTableWithoutKeys(request, format.table)
        }
      }
    }
}
