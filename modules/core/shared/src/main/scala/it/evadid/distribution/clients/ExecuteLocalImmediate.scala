package it.evadid.distribution.clients

import it.evadid.distribution.*
import it.evadid.distribution.executor.Executor
import it.evadid.distribution.*

import scala.concurrent.Future

case class ExecuteLocalImmediate(val handlers: List[Executor]) extends LocalExecutionClient {


  // Executes command synchronously and immediately returns the result

  override def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionInfo] = Future.successful(executeCommandSync(executionCommand))

  def executeCommandSync(executionCommand: ExecutionCommand): ExecutionInfo = executeWithFirstHandler(executionCommand)

}
