package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoUntyped
import it.evadid.distribution.command.ExecutionResult.ExecutionResultUntyped
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait ExecutionClient {

  protected given ExecutionContext =ExecutionContext.global

  def canExecuteCommand(executionCommand: ExecutionCommand): Boolean

  private[distribution] def handleExecutionRaw(executionCommand: ExecutionCommand, logger: Logger): Future[Map[String, String]] = {
    handleExecution(executionCommand).map(_.response.toOption.get)(using ExecutionContext.global)
  }

  def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionClientResponse]

  def handleExecution(executionCommand: ExecutionCommand): Future[ExecutionClientResponse] = {
    println("[WARN] creating new logger for Execution of command: " + executionCommand.name + " with params: " + executionCommand.params.mkString(", "))
    handleExecution(executionCommand, Logger.withNameAndPrefixes(Some(this.toString), PrintToStdLogger.printWarnAndError))
  }

  def handleCommand(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionInfo] = {
    val timestampRequested: LocalDateTime = LocalDateTime.now()
    handleExecution(executionCommand, logger).map(ExecutionClient.finishUnsafeWithResponse(executionCommand, timestampRequested, _))(using ExecutionContext.global)
  }

  def handleCommand(executionCommand: ExecutionCommand): Future[ExecutionInfo] = {
    println("[WARN] creating new logger for Execution of command: " + executionCommand.name + " with params: " + executionCommand.params.mkString(", "))
    handleCommand(executionCommand, Logger.withNameAndPrefixes(Some(this.toString), PrintToStdLogger.printWarnAndError))
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
        throw err
      case Right(resMap) =>
        val result = ExecutionResultUntyped(resMap, response.loggerOut, response.loggerError)
        ExecutionInfoUntyped(executionCommand, result, history)
    }
  }

}




