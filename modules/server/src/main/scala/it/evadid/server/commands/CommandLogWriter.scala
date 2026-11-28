package it.evadid.server.commands

import it.evadid.core.datastructures.user.User
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.SubmissionCommands.SubmitSessionEvent
import it.evadid.server.BlockingExecution
import it.evadid.server.database.{Database, SqlStatements}
import it.evadid.sql.{CommandLog, DatabaseConfigWithCredentials}
import it.evadid.util.logging.Logger

import java.net.InetAddress
import java.sql.{Connection, Timestamp}
import scala.concurrent.Future
import scala.util.control.NonFatal
import upickle.default.{read, write}

/** Records received commands in their [[CommandLog]] table of the backend database, without delaying the command. */
final class CommandLogWriter(database: Option[DatabaseConfigWithCredentials]) {

  /** Writes the log row in the background; failures are logged, never reported to the command. Without a configured
    * database nothing is written (the server warned at startup).
    */
  def record(command: ExecutionCommand, context: CommandAccess.RequestContext, logger: Logger): Future[Unit] =
    (command.storeLogIntoTable, database) match {
      case (Some(log), Some(configured)) => BlockingExecution {
        try Database.withConnection(configured, "storing the command log", logger)(CommandLogWriter.insert(log, command, context, logger, _))
        catch case NonFatal(error) => logger.logExceptionWarn("Could not store command log for " + command.name, error)
      }
      case _ => Future.unit
    }
}

object CommandLogWriter {
  private[server] def payloadForLog(command: ExecutionCommand): String =
    command.stripPrivacySensitiveInformation().toJson

  /** The submitted session as typed JSON without destination credentials; an unreadable submission is not logged. */
  private[server] def submittedSessionForLog(command: ExecutionCommand): String =
    write(read[SubmitSessionEvent](command.json).sessionData.withoutCredentials)

  /** Inserts the command's row into `log` on `connection`. */
  private[commands] def insert(log: CommandLog, command: ExecutionCommand, context: CommandAccess.RequestContext,
                              logger: Logger, connection: Connection): Unit = {
    val timestamp = Timestamp.valueOf(context.received)
    val address = context.remoteAddress.getHostAddress
    val userId = context.user.map(_.id).getOrElse(User.Anonymous.id)
    val statements = SqlStatements(connection, logger)
    val written = log match {
      case CommandLog.CommandAudit => statements.update(insertCommandAudit,
        timestamp, address, command.name, payloadForLog(command), userId, address)
      case CommandLog.SyncAudit => statements.update(insertSyncAudit,
        timestamp, address, command.name, payloadForLog(command), userId, address)
      case CommandLog.SessionSubmission => statements.update(insertSessionSubmission,
        timestamp, address, userId, submittedSessionForLog(command))
    }
    if written <= 0 then logger.logWarn("Failed db logging for command: " + command.name + " (" + payloadForLog(command) + ")")
  }

  private val insertCommandAudit =
    "INSERT INTO `commandLog` (`timestamp`, `origin`, `type`, `payload`, `userid`, `ipAddress`) VALUES (?, ?, ?, ?, ?, ?)"
  private val insertSyncAudit =
    "INSERT INTO `syncedLog` (`timestamp`, `origin`, `type`, `payload`, `userid`, `ipAddress`) VALUES (?, ?, ?, ?, ?, ?)"
  private val insertSessionSubmission =
    "INSERT INTO `submissionLog` (`timestamp`, `ipAddress`, `userid`, `sessionDataSerialized`) VALUES (?, ?, ?, ?)"
}
