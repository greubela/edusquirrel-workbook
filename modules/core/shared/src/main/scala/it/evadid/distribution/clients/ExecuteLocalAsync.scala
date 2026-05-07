package it.evadid.distribution.clients

import it.evadid.distribution.*
import it.evadid.distribution.*
import it.evadid.distribution.executor.Executor

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class ExecuteLocalAsync(handlers: List[Executor], ec: ExecutionContext = ExecutionContext.global) extends LocalExecutionClient {


  override def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionInfo] = Future {
    executeWithFirstHandler(executionCommand)
  }(using ec)
  
}
