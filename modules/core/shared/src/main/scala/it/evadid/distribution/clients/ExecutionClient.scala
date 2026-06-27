package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoUntyped
import it.evadid.distribution.command.ExecutionResult.ExecutionResultUntyped
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.Logger

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait ExecutionClient {

  def canExecuteCommand(executionCommand: ExecutionCommand): Boolean

  //def handleExecution(executionCommand: ExecutionCommand, logger: Logger): AsyncData[Nothing, ExecutionInfo]

  def executeCommand(executionCommand: ExecutionCommand, logger: Logger): Future[Map[String, String]]

  def handleExecution(executionCommand: ExecutionCommand): Future[ExecutionClientResponse]

  def handleCommand(executionCommand: ExecutionCommand): Future[ExecutionInfo] = {
    val timestampRequested: LocalDateTime = LocalDateTime.now()
    handleExecution(executionCommand).map(ExecutionClient.finishUnsafeWithResponse(executionCommand, timestampRequested, _))(using ExecutionContext.global)
  }

  def makeSynchronized(ec: ExecutionContext): ExecutionClient = SynchronizedExecutionClient(this, ec)

  def allUnderlyingClients: List[ExecutionClient] = List(this)

  def pooledWith(other: ExecutionClient): ExecutionClient = ExecutionClientPool(allUnderlyingClients ++ other.allUnderlyingClients)

}

object ExecutionClient {


  def apply(clients: List[ExecutionClient]): ExecutionClient = ExecutionClientPool(clients)


  def finishUnsafeWithResponse(executionCommand: ExecutionCommand, timestampRequested: LocalDateTime, response: ExecutionClientResponse): ExecutionInfo = {
    val history = ExecutionHistory(timestampRequested, response.timestampReceived, response.timestampStarted, response.timestampFinished)
    response.response.match {
      case Left(err) =>
        println("ExecutionClientResponse indicated an error: \n" + response)
        throw err
      case Right(resMap) =>
        val result = ExecutionResultUntyped(resMap, response.loggerOut, response.loggerError)
        ExecutionInfoUntyped(executionCommand, result, history)
    }
  }

}




