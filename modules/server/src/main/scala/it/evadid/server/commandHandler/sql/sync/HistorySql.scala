package it.evadid.server.commandHandler.sql.sync

import it.evadid.util.logging.Logger
import it.evadid.distribution.commandTypes.SQLCommands.{BulkStoreToDbRequest, StoreToDbRequest}
import it.evadid.workbook.interaction.sync.SyncSuccess
import it.evadid.server.database.SqlFailure
import it.evadid.core.util.io.ParsingContext
import java.sql.{Connection, PreparedStatement, ResultSet, Types}
import scala.util.Using

/** Shared JDBC resource handling and transaction ownership for both history layouts. */
abstract class HistorySql(connection: Connection, logger: Logger) {
  def store(request: StoreToDbRequest): SyncSuccess

  /** One transaction and deterministic lock order across the entire network batch. */
  final def storeAll(request: BulkStoreToDbRequest): SyncSuccess = transaction {
    request.entries.sortBy(entry => (entry.syncContext.programId, entry.syncContext.scenarioId,
      entry.syncContext.userId, entry.syncContext.keyForSerialisation))
      .foldLeft(SyncSuccess.emptyNow())((result, entry) => result.combine(store(entry)))
  }

  private def prepared[A](sql: String, values: Seq[Any])(run: PreparedStatement => A): A =
    SqlFailure(s"executing history SQL ${ParsingContext.preview(sql)}", logger) {
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
  protected def query[A](sql: String, values: Seq[Any])(read: ResultSet => A): Seq[A] =
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
