package it.evadid.distribution.clients

import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataStateFinished
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoUntyped
import it.evadid.util.Logger

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait ExecutionClient {

  def canExecuteCommand(executionCommand: ExecutionCommand): Boolean

  //def handleExecution(executionCommand: ExecutionCommand, logger: Logger): AsyncData[Nothing, ExecutionInfo]

  protected def executeCommand(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionResult]

  protected def handleExecution(executionCommand: ExecutionCommand): Future[(ExecutionResult, ExecutionDuration, Logger)] = {
    val timeExecutionStarted: LocalDateTime = LocalDateTime.now()
    val logger = Logger()
    val resultFuture: Future[ExecutionResult] = executeCommand(executionCommand, logger)
    resultFuture.map(result => {
      val timeExecutionFinished = LocalDateTime.now()
      val res = (result, ExecutionDuration(timeExecutionStarted, timeExecutionFinished), logger)
      res
    })(using ExecutionContext.global)
  }

  def handleCommand(executionCommand: ExecutionCommand, logger: Logger): Future[AsyncDataStateFinished[Nothing, ExecutionInfo]]

  def makeSynchronized(ec: ExecutionContext): ExecutionClient = SynchronizedExecutionClient(this, ec)

  def allUnderlyingClients: List[ExecutionClient] = List(this)

  def pooledWith(other: ExecutionClient): ExecutionClient = ExecutionClientPool(allUnderlyingClients ++ other.allUnderlyingClients)

}

object ExecutionClient {


  def apply(clients: List[ExecutionClient]): ExecutionClient = ExecutionClientPool(clients)

}




