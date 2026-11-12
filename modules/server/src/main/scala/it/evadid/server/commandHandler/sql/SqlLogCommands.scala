package it.evadid.server.commandHandler.sql

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.User
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.sql.DatabaseTable
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.SyncSuccess

import java.net.InetAddress
import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime
import scala.util.Using
import scala.util.control.NonFatal

object SqlLogCommands {

  private[server] def payloadForLog(command: ExecutionCommand): String =
    command.stripPrivacySensitiveInformation().toJson

  def handleLog(commandReceived: LocalDateTime, executionCommand: ExecutionCommand, verifiedToken: Option[SignedToken], remoteAddress: InetAddress, logger: Logger): Unit = {
    executionCommand.storeLogIntoTable.foreach { table =>
      try Using.resource(it.evadid.server.database.DatabaseRequestConnection.resolve(
        table.inDatabase.getOrElse(it.evadid.sql.DatabaseConfigWithCredentials.readFromEnv()),
        it.evadid.sql.DatabaseConfigWithCredentials.readFromEnv()).newConnection()) { connection =>
        handleLog(commandReceived, executionCommand, verifiedToken, remoteAddress, logger, connection)
      }
      catch case NonFatal(error) => logger.logExceptionWarn("Could not store command log for " + executionCommand.name, error)
    }
  }

  private[sql] def handleLog(commandReceived: LocalDateTime, executionCommand: ExecutionCommand,
      verifiedToken: Option[SignedToken], remoteAddress: InetAddress, logger: Logger, connection: Connection): Unit = {
    val control = SqlLogCommands(connection, logger)
    val res = control.logCommand(commandReceived, executionCommand, verifiedToken, remoteAddress, executionCommand.storeLogIntoTable)
    if (res.elementsAdded <= 0) logger.logWarn("Failed db logging for command: " + executionCommand.name + " (" + payloadForLog(executionCommand) + ")")
  }

}

class SqlLogCommands(
                      connection: Connection,
                      logger: Logger,
                    ) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(logger)

  def logCommand(commandReceived: LocalDateTime, executionCommand: ExecutionCommand, verifiedToken: Option[SignedToken],
                 remoteAddress: InetAddress, storeLogIntoTable: Option[DatabaseTable]): SyncSuccess = {
    if storeLogIntoTable.isEmpty then return SyncSuccess(0, 0, 0, LocalDateTime.now())
    val storeOrigin: String = remoteAddress.getHostAddress
    val storeTimestamp: Timestamp = Timestamp.valueOf(commandReceived)
    val storeType: String = executionCommand.name
    val storePayload: String = SqlLogCommands.payloadForLog(executionCommand)
    val userId = verifiedToken.map(_.info.user.id).getOrElse(User.Anonymous.id)

    val sql =
      s"""
         |INSERT INTO `${storeLogIntoTable.get.name}` (`timestamp`, `origin`, `type`, `payload`, `userid`, `ipAddress`)
         |VALUES (?, ?, ?, ?, ?, ?)
         |""".stripMargin

    val res = Using.resource(connection.prepareStatement(sql)) { stmt =>
      stmt.setTimestamp(1, storeTimestamp)
      stmt.setString(2, storeOrigin)
      stmt.setString(3, storeType)
      stmt.setString(4, storePayload)
      stmt.setString(5, userId)
      stmt.setString(6, remoteAddress.getHostAddress)

      generic.executeUpdate(stmt)
    }
    SyncSuccess(res, 0, 0, LocalDateTime.now())
  }


}
