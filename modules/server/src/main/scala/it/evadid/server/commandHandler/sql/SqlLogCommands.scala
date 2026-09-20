package it.evadid.server.commandHandler.sql

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess

import java.net.InetAddress
import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime

object SqlLogCommands {

  def handleLog(commandReceived: LocalDateTime, executionCommand: ExecutionCommand, verifiedToken: Option[SignedToken], remoteAddress: InetAddress, logger: Logger): Unit = {
    val config = DatabaseConfig.readFromEnv()
    val connection = config.newConnection()
    val control = SqlLogCommands(connection, logger)
    val res = control.logCommand(commandReceived, executionCommand, verifiedToken, remoteAddress, logger)
    if (res.elementsAdded <= 0) logger.logWarn("Failed db logging for command: " + executionCommand.name + " (" + executionCommand.toJson + ")")
  }

}

class SqlLogCommands(
                      connection: Connection,
                      logger: Logger,
                    ) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(connection, logger)

  def logCommand(commandReceived: LocalDateTime, executionCommand: ExecutionCommand, verifiedToken: Option[SignedToken],remoteAddress: InetAddress, logger: Logger): SyncSuccess = {
    val storeOrigin: String = remoteAddress.getCanonicalHostName
    val storeTimestamp: Timestamp = Timestamp.valueOf(commandReceived)
    val storeType: String = executionCommand.name
    val storePayload: String = executionCommand.toJson
    val storeInfo = verifiedToken.map(_.info.toJson).getOrElse("")

    val sql =
      s"""
         |INSERT INTO `commandLog` (`timestamp`, `origin`, `type`, `payload`, `tokenInfo`)
         |VALUES (?, ?, ?, ?, ?)
         |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setTimestamp(1, storeTimestamp)
    stmt.setString(2, storeOrigin)
    stmt.setString(3, storeType)
    stmt.setString(4, storePayload)
    stmt.setString(5, storeInfo)

    val res = generic.executeUpdate(stmt)
    SyncSuccess(res, 0, 0, LocalDateTime.now())
  }


}
