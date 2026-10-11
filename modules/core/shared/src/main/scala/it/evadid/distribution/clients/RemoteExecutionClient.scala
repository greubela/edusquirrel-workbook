package it.evadid.distribution.clients

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.*
import it.evadid.util.logging.Logger
import upickle.default

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*

trait RemoteExecutionClient extends ExecutionClient {

  def hostname: String

  def port: Int

  override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = true

  protected def sendTo(logger: Logger, hostname: String, port: Int, command: ExecutionCommand): Future[ExecutionClientResponse]

  override def handleCommandExecution(logger: Logger, executionCommand: ExecutionCommand): Future[ExecutionClientResponse] = {
    sendTo(logger, hostname, port, executionCommand)
  }
}

