package it.evadid.distribution.clients

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.Logger

import scala.concurrent.Future

trait RemoteExecutionClient extends ExecutionClient {

  def ip: String
  def port: Int

  override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = true

  protected def sendTo(ip: String, port: Int, command: ExecutionCommand): Future[ExecutionClientResponse]

  override def handleExecution(executionCommand: ExecutionCommand): Future[ExecutionClientResponse] = {
    sendTo(ip, port, executionCommand)
  }
}
