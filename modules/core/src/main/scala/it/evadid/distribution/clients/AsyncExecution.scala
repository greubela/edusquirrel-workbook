package it.evadid.distribution.clients

import it.evadid.distribution.*
import it.evadid.distribution.ExecutionCommand.ExecutionInfo

import scala.concurrent.{ExecutionContext, Future}

case class AsyncExecution(commandHandler: Executor, ec: ExecutionContext = ExecutionContext.global) extends ExecutionClient {


  override def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionInfo] = Future {
    commandHandler.execute(executionCommand)
  }(using ec)
  
}
