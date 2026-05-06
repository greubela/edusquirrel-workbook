package it.evadid.distribution

import it.evadid.distribution.ExecutionCommand.{ExecutionInfo, ExecutionResult}
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.executor.Executor

import scala.concurrent.Future

trait ExecutionServer {

  def localExecutionClient: ExecutionClient

  def handleExecution(executionCommand: ExecutionCommand): Future[ExecutionInfo] = {
    localExecutionClient.executeCommand(executionCommand)
  }

}
