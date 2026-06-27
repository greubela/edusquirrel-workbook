package it.evadid.distribution.clients


import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.core.datastructures.state.async.AsyncDataState.{AsyncDataStateFinished, AsyncDataSuccess}
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoUntyped
import it.evadid.util.Logger

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.*

/**
 * An execution client that executes commands locally (and, as such, builds the ExecutionInfo itself)
 */
trait LocalExecutionClient extends ExecutionClient {

  protected def getExecutionContext: ExecutionContext = ExecutionContext.global

  override def handleCommand(executionCommand: ExecutionCommand, logger: Logger): Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = {
    val timeExecutionRequested: LocalDateTime = LocalDateTime.now()
    val fut: Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = handleExecution(executionCommand, logger).map {
      (result: ExecutionResult, duration: ExecutionDuration, logger: Logger) => {
        val history = ExecutionHistory(timeExecutionRequested, timeExecutionRequested, duration.timeExecutionStarted, duration.timeExecutionFinished)
        AsyncDataSuccess[Nothing, ExecutionInfo](ExecutionInfoUntyped(executionCommand, result, history))
      }
    }(using getExecutionContext)
    fut
  }

  /*private def execute(executionCommand: ExecutionCommand): Future[(ExecutionResult, ExecutionDuration, Logger)] = {
    val timeExecutionStarted = LocalDateTime.now()
    val logger = Logger()
    val resultFuture = calculateResult(executionCommand, logger)
    resultFuture.map(result => {
      val timeExecutionFinished = LocalDateTime.now()
      val res = (result, ExecutionDuration(timeExecutionStarted, timeExecutionFinished), logger)
      res
    })(using ExecutionContext.global)
  }*/

  def calculateResult(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionResult]

  protected def resultFromValue[T](value: T, toStringFunc: T => String = (str: T) => str.toString): ExecutionResult = ExecutionResult(Map("result" -> toStringFunc(value)), "", "")


}
