package it.evadid.distribution.clients


import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.logging.Logger

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.*

/**
 * An execution client that executes commands locally (and, as such, builds the ExecutionInfo itself)
 */
trait LocalExecutionClient extends ExecutionClient {

  protected def executeCommand(executionCommand: ExecutionCommand, logger: Logger): Future[Map[String, String]]

  private[distribution] override def handleExecutionRaw(executionCommand: ExecutionCommand, logger: Logger): Future[Map[String, String]] = {
    executeCommand(executionCommand, logger)
  }

  override def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionClientResponse] = {
    val timestampReceived: LocalDateTime = LocalDateTime.now()
    handleExecutionRaw(executionCommand, logger).map { (resMap: Map[String, String]) =>
      ExecutionClientResponse(timestampReceived, timestampReceived, LocalDateTime.now(), Right(resMap), Some(executionCommand), logger.getOut(), logger.getErr())
    }.recover { (err: Throwable) =>
      val newErr = SerializedException("Error in executeCommand: " + err.getMessage, err)
      ExecutionClientResponse(timestampReceived, timestampReceived, LocalDateTime.now(), Left(newErr), Some(executionCommand), logger.getOut(), logger.getErr())
    }
  }


}
