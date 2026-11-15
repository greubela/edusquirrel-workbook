package it.evadid.server.database

import it.evadid.util.logging.Logger
import java.sql.SQLException

/** SQL diagnostics never include prepared-statement bindings in transport errors. */
object SqlFailure {
  private final class ContextualSqlException(message: String, state: String, code: Int)
    extends SQLException(message, state, code)

  def apply[A](operation: String, logger: Logger)(run: => A): A =
    try run
    catch {
      case contextual: ContextualSqlException => throw contextual
      case error: SQLException =>
        logger.logExceptionWarn(s"SQL failure while $operation", error)
        val reason = Option(error.getSQLState).map(_.take(2)) match {
          case Some("08") => "Could not connect to or communicate with the database; check availability and connection settings."
          case Some("28") => "The database rejected the credentials; check the configured database user and password."
          case Some("23") => "A database constraint rejected the data; check unique keys and referenced records."
          case Some("22") => "A value exceeds the column limits or has an unsupported database format."
          case Some("40") => "The transaction conflicted with another operation; retry the request."
          case Some("42") => "The database rejected the query; check table/column names and database permissions."
          case _ => "The database could not complete the operation; check the server database logs."
        }
        // Vendor exceptions may contain credentials or answer data. Retain them in
        // server logs, not the cause chain serialized into the browser response.
        throw new ContextualSqlException(
          s"SQL error while $operation: $reason (SQLState=${Option(error.getSQLState).getOrElse("unknown")}, errorCode=${error.getErrorCode})",
          error.getSQLState, error.getErrorCode)
    }
}
