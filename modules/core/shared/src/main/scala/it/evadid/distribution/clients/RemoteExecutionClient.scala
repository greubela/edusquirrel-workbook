package it.evadid.distribution.clients

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.logging.Logger

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait RemoteExecutionClient extends ExecutionClient {

  def hostname: String

  def port: Int

  override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = true

  protected def sendTo(hostname: String, port: Int, command: ExecutionCommand): Future[ExecutionClientResponse]

  override def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionClientResponse] = {
    val res = sendTo(hostname, port, executionCommand)
    res.map { rec => {
      val timestampReceivedBack: LocalDateTime = LocalDateTime.now()
      logger.logInfo("Received response from " + hostname + ":" + port + " at " + timestampReceivedBack + ": " + rec.response.toString)
      logger.logFromExternalInfo(rec.loggerOut)
      logger.logFromExternalError(rec.loggerError)
      ExecutionClientResponse(rec.timestampReceived, rec.timestampStarted, timestampReceivedBack, rec.response, rec.parsedExecutionCommand, logger.getOut(), logger.getErr())
    }
    }(using ExecutionContext.global)
  }
}
