package it.evadid.server.commandHandler.sql

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.SyncSuccess

import java.net.InetAddress
import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime

object SqlLogCommands {

  private[server] def payloadForLog(command: ExecutionCommand): String =
    if command.name != LLMCommands.feedbackLlmCommandFactory.name then command.toJson
    else {
      def length(field: String): ujson.Value = command.json.objOpt
        .flatMap(_.get(field)).flatMap(_.strOpt)
        .fold[ujson.Value](ujson.Null)(value => ujson.Num(value.length))
      ujson.write(ujson.Obj("name" -> command.name, "redacted" -> true,
        "promptLength" -> length("prompt"), "systemPromptLength" -> length("systemPrompt")))
    }

  def handleLog(commandReceived: LocalDateTime, executionCommand: ExecutionCommand, verifiedToken: Option[SignedToken], remoteAddress: InetAddress, logger: Logger): Unit = {
    val config = DatabaseConfig.readFromEnv()
    val connection = config.newConnection()
    handleLog(commandReceived, executionCommand, verifiedToken, remoteAddress, logger, connection)
  }

  private[sql] def handleLog(commandReceived: LocalDateTime, executionCommand: ExecutionCommand,
      verifiedToken: Option[SignedToken], remoteAddress: InetAddress, logger: Logger, connection: Connection): Unit = {
    val control = SqlLogCommands(connection, logger)
    val res = control.logCommand(commandReceived, executionCommand, verifiedToken, remoteAddress, logger)
    if (res.elementsAdded <= 0) logger.logWarn("Failed db logging for command: " + executionCommand.name + " (" + payloadForLog(executionCommand) + ")")
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
    val storePayload: String = SqlLogCommands.payloadForLog(executionCommand)
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
