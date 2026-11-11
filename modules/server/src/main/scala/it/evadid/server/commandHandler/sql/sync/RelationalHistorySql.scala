package it.evadid.server.commandHandler.sql.sync

import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.grading.GradingResult
import it.evadid.workbook.interaction.sync.UpdateImportance
import java.sql.{Connection, PreparedStatement, ResultSet, Types}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.util.{Try, Using}

/** Shared JDBC resource handling and relational row decoding; contains no history writes. */
abstract class RelationalHistorySql(connection: Connection, logger: Logger) {
  private val timestampFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS")
  protected def time(value: ujson.Value): String = LocalDateTime.parse(value.str).format(timestampFormat)
  protected def restoredTime(value: String): ujson.Value = ujson.Str(LocalDateTime.parse(value).toString)

  private def prepared[A](sql: String, values: Seq[Any])(run: PreparedStatement => A): A =
    Using.resource(connection.prepareStatement(sql)) { statement =>
      values.zipWithIndex.foreach { (value, index) => value match {
        case null => statement.setNull(index + 1, Types.VARCHAR)
        case number: Long => statement.setLong(index + 1, number)
        case other => statement.setString(index + 1, other.toString)
      }}
      run(statement)
    }
  protected def update(sql: String, values: Any*): Int = prepared(sql, values)(_.executeUpdate())
  protected def query[A](sql: String, values: Seq[Any])(read: ResultSet => A): List[A] =
    prepared(sql, values) { statement =>
      Using.resource(statement.executeQuery())(rows => Iterator.continually(rows.next()).takeWhile(identity).map(_ => read(rows)).toList)
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
  private def readOptional(value: String): ujson.Value =
    Option(value).map(ujson.read(_)).getOrElse(ujson.Null)

  protected def mergeGrade(previous: GradingResult[ujson.Value, ujson.Value], incoming: GradingResult[ujson.Value, ujson.Value]) = {
    require(previous.grader.key == incoming.grader.key)
    if previous.grader == incoming.grader then previous.merge(incoming)
    else if previous.gradingTime.isAfter(incoming.gradingTime) then previous else incoming
  }

  protected def answerJson(row: ResultSet): ujson.Value =
    ujson.Obj("value" -> ujson.read(row.getString("serialized_data")),
      "timestamp" -> restoredTime(row.getString("timestamp")),
      "updateImportance" -> UpdateImportance.valueOf(row.getString("update_importance")).toString)

  protected def gradeJson(row: ResultSet, state: ujson.Value): ujson.Value = {
    val regular = ujson.read(row.getString("serialized_data"))
    ujson.Obj("grader" -> ujson.Str(row.getString("grader_definition")), "gradedState" -> state,
      "gradingStatus" -> regular("gradingStatus"), "feedbackInformation" -> regular.obj.getOrElse("feedbackInformation", ujson.Null),
      "additionalData" -> readOptional(row.getString("additional_data")), "gradingTime" -> restoredTime(row.getString("timestamp")))
  }
  protected def scaffoldingJson(row: ResultSet, state: ujson.Value): ujson.Value =
    ujson.Obj("forState" -> state, "scaffoldingStatus" -> ujson.read(row.getString("serialized_data")),
      "scaffoldingEntity" -> ujson.read(row.getString("scaffolding_entity")),
      "additionalInformation" -> readOptional(row.getString("additional_data")), "scaffoldingTime" -> restoredTime(row.getString("timestamp")))

  protected def safe[A](description: String)(read: => A): Option[A] = Try(read).fold(
    _ => { logger.logWarn("Discarded malformed relational history record: " + description); None }, Some(_))

}
