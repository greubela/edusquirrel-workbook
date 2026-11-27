package it.evadid.server.commandHandler.sql

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.User
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.SubmissionCommands.SubmitSessionEvent
import it.evadid.server.database.DatabaseRequestConnection
import it.evadid.sql.{BackendDatabase, CommandLog, DatabaseConfigWithCredentials, DatabaseTable}
import it.evadid.util.logging.Logger

import java.net.InetAddress
import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime
import scala.util.Using
import scala.util.control.NonFatal
import upickle.default.{read, write}

object SqlLogCommands {

  private[server] def payloadForLog(command: ExecutionCommand): String =
    command.stripPrivacySensitiveInformation().toJson

  /** The submitted session as typed JSON without destination credentials; an unreadable submission is not logged. */
  private[server] def submittedSessionForLog(command: ExecutionCommand): String =
    write(read[SubmitSessionEvent](command.json).sessionData.withoutCredentials)

  def handleLog(commandReceived: LocalDateTime, executionCommand: ExecutionCommand, verifiedToken: Option[SignedToken], remoteAddress: InetAddress, logger: Logger): Unit = {
    executionCommand.storeLogIntoTable.foreach { log =>
      try Using.resource(DatabaseRequestConnection.resolve(log.table.inDatabase.getOrElse(BackendDatabase),
        DatabaseConfigWithCredentials.readFromEnv()).newConnection()) { connection =>
        handleLog(commandReceived, executionCommand, verifiedToken, remoteAddress, logger, connection)
      }
      catch case NonFatal(error) => logger.logExceptionWarn("Could not store command log for " + executionCommand.name, error)
    }
  }

  private[sql] def handleLog(commandReceived: LocalDateTime, executionCommand: ExecutionCommand,
      verifiedToken: Option[SignedToken], remoteAddress: InetAddress, logger: Logger, connection: Connection): Unit = {
    val control = SqlLogCommands(connection, logger)
    val res = control.logCommand(commandReceived, executionCommand, verifiedToken, remoteAddress, executionCommand.storeLogIntoTable)
    if (res <= 0) logger.logWarn("Failed db logging for command: " + executionCommand.name + " (" + payloadForLog(executionCommand) + ")")
  }

}

class SqlLogCommands(connection: Connection, logger: Logger) {

  private val generic: GenericSqlFunctionality = new GenericSqlFunctionality(logger)

  def logCommand(commandReceived: LocalDateTime, executionCommand: ExecutionCommand, verifiedToken: Option[SignedToken],
                 remoteAddress: InetAddress, storeLogIntoTable: Option[CommandLog]): Int = {
    val timestamp = Timestamp.valueOf(commandReceived)
    val address = remoteAddress.getHostAddress
    val userId = verifiedToken.map(_.info.user.id).getOrElse(User.Anonymous.id)
    storeLogIntoTable.fold(0) {
      case CommandLog.Audit(table) => insert(table, Seq("timestamp" -> timestamp, "origin" -> address,
        "type" -> executionCommand.name, "payload" -> SqlLogCommands.payloadForLog(executionCommand),
        "userid" -> userId, "ipAddress" -> address))
      case CommandLog.SessionSubmission(table) => insert(table, Seq("timestamp" -> timestamp, "ipAddress" -> address,
        "userid" -> userId, "sessionDataSerialized" -> SqlLogCommands.submittedSessionForLog(executionCommand)))
    }
  }

  /** Inserts one row; column names are fixed by [[CommandLog]] and the table name is a validated identifier. */
  private def insert(table: DatabaseTable, row: Seq[(String, Timestamp | String)]): Int = {
    val sql = s"INSERT INTO `${table.name}` (${row.map(column => s"`${column._1}`").mkString(", ")}) " +
      s"VALUES (${row.map(_ => "?").mkString(", ")})"
    Using.resource(connection.prepareStatement(sql)) { stmt =>
      row.map(_._2).zipWithIndex.foreach {
        case (timestamp: Timestamp, index) => stmt.setTimestamp(index + 1, timestamp)
        case (text: String, index) => stmt.setString(index + 1, text)
      }
      generic.executeUpdate(stmt)
    }
  }

}
