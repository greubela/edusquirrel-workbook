package it.evadid.server.database

import it.evadid.core.util.io.ParsingContext
import it.evadid.util.logging.Logger
import java.sql.{Connection, PreparedStatement, ResultSet, Timestamp}
import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.util.Using

/** Prepared statements with typed bindings on a caller-owned connection.
  * Each statement and result set is closed once used; failures pass through [[SqlFailure]],
  * so neither diagnostics nor logs contain bound values.
  */
final class SqlStatements(connection: Connection, logger: Logger) {
  import SqlStatements.*

  def update(sql: String, values: Value*): Int = prepared(sql, values)(_.executeUpdate())

  def query[A](sql: String, values: Value*)(read: ResultSet => A): Seq[A] =
    prepared(sql, values)(statement => rows(statement.executeQuery())(read))

  private def prepared[A](sql: String, values: Seq[Value])(run: PreparedStatement => A): A =
    SqlFailure(s"executing SQL ${ParsingContext.preview(sql)}", logger) {
      require(values.forall(_ != null), "SQL parameters must not be null")
      Using.resource(connection.prepareStatement(sql)) { statement =>
        values.zipWithIndex.foreach((value, index) => bind(statement, index + 1, value))
        run(statement)
      }
    }
}

object SqlStatements {
  type Value = String | Long | Instant

  /** Database times are UTC wall times, independent of the server's zone. */
  def timestamp(instant: Instant): Timestamp = Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC))
  def instant(timestamp: Timestamp): Instant = timestamp.toLocalDateTime.toInstant(ZoneOffset.UTC)

  /** Reads every row of `resultSet`, starting at the first, and closes it. */
  def rows[A](resultSet: ResultSet)(read: ResultSet => A): Seq[A] =
    Using.resource(resultSet)(rs => Iterator.continually(rs.next()).takeWhile(identity).map(_ => read(rs)).toList)

  private def bind(statement: PreparedStatement, position: Int, value: Value): Unit = value match {
    case number: Long => statement.setLong(position, number)
    case text: String => statement.setString(position, text)
    case instant: Instant => statement.setTimestamp(position, timestamp(instant))
  }
}
