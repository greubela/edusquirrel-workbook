package it.evadid.distribution.clients


import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.Logger

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.*

/**
 * An execution client that executes commands locally (and, as such, builds the ExecutionInfo itself)
 */
trait LocalExecutionClient extends ExecutionClient {

  protected given ExecutionContext = ExecutionContext.global

  override def handleExecution(executionCommand: ExecutionCommand): Future[ExecutionClientResponse] = {
    val timestampReceived: LocalDateTime = LocalDateTime.now()
    val logger = Logger()
    executeCommand(executionCommand, logger).map { (resMap: Map[String, String]) =>
      ExecutionClientResponse(timestampReceived, timestampReceived, LocalDateTime.now(), Right(resMap), Some(executionCommand), logger.getOut(), logger.getErr())
    }.recover { (err: Throwable) =>
      val newErr = SerializedException("Error in executeCommand: " + err.getMessage, err)
      ExecutionClientResponse(timestampReceived, timestampReceived, LocalDateTime.now(), Left(newErr), Some(executionCommand), logger.getOut(), logger.getErr())
    }
  }


  /*override def handleCommand(executionCommand: ExecutionCommand, logger: Logger): Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = {

  }*/

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

  //def calculateResult(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionResult]

  //protected def resultFromValue[T](value: T, toStringFunc: T => String = (str: T) => str.toString): ExecutionResult = ExecutionResult(Map("result" -> toStringFunc(value)), "", "")



}
