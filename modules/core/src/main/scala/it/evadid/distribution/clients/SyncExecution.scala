package it.evadid.distribution.clients

import it.evadid.distribution.{ExecutionClient, ExecutionCommand}
import it.evadid.distribution.ExecutionCommand.{ExecutionInfo, ExecutionResult}

import it.evadid.distribution.*
import scala.concurrent.Future

case class SyncExecution(handleCommand: Executor) extends ExecutionClient{

  
  // Executes command synchronously and immediately returns the result
  
  override def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionInfo] = Future.successful(handleCommand.execute(executionCommand))
  
  
}
