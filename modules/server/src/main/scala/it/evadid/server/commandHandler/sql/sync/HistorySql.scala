package it.evadid.server.commandHandler.sql.sync

import it.evadid.util.logging.Logger
import it.evadid.server.database.SqlFailure
import it.evadid.core.util.io.ParsingContext
import upickle.default.{ReadWriter, read}
import it.evadid.workbook.abstractions.grading.GradingResult
import it.evadid.workbook.interaction.sync.UpdateImportance
import java.sql.{Connection, PreparedStatement, ResultSet, Types}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.util.{Try, Using}

/** Shared JDBC resource handling and relational row decoding; contains no history writes. */
abstract class HistorySql(connection: Connection, logger: Logger) {
  private def prepared[A](sql: String, values: Seq[Any])(run: PreparedStatement => A): A =
    SqlFailure(s"executing relational history SQL ${ParsingContext.preview(sql)}", logger) {
      Using.resource(connection.prepareStatement(sql)) { statement =>
        values.zipWithIndex.foreach { (value, index) => value match {
          case null => statement.setNull(index + 1, Types.VARCHAR)
          case number: Long => statement.setLong(index + 1, number)
          case other => statement.setString(index + 1, other.toString)
        }}
        run(statement)
      }
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

}
