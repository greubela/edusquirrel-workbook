package it.evadid.server.database

import it.evadid.sql.DatabaseConfigWithCredentials
import it.evadid.util.logging.Logger
import java.sql.{Connection, DriverManager}
import scala.util.Using
import scala.util.control.NonFatal

/** The only way the server opens JDBC connections: bounded timeouts, sanitized failures and guaranteed closing. */
object Database {
  /** User-supplied databases are reachable too, so the driver never reads client files or deserializes objects. */
  private val connectionOptions = "connectTimeout=5000&socketTimeout=60000&allowLoadLocalInfile=false&autoDeserialize=false"

  /** Opens a connection that the caller owns and closes. */
  def open(config: DatabaseConfigWithCredentials): Connection =
    DriverManager.getConnection(s"${config.jdbcUrl}?$connectionOptions", config.user, config.password)

  /** Runs `run` on a new connection, closed afterwards; SQL failures are reported through [[SqlFailure]]. */
  def withConnection[A](config: DatabaseConfigWithCredentials, operation: String, logger: Logger)(run: Connection => A): A =
    SqlFailure(operation, logger)(Using.resource(open(config))(run))

  /** Runs `run` in a repeatable-read transaction, or inside the caller's already open transaction. */
  def transaction[A](connection: Connection)(run: => A): A =
    if !connection.getAutoCommit then run
    else {
      val isolation = connection.getTransactionIsolation
      connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ)
      connection.setAutoCommit(false)
      try {
        val result = run
        connection.commit()
        result
      } catch {
        case NonFatal(error) =>
          connection.rollback()
          throw error
      } finally {
        connection.setAutoCommit(true)
        connection.setTransactionIsolation(isolation)
      }
    }
}
