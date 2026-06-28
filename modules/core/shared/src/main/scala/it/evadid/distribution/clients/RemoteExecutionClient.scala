package it.evadid.distribution.clients

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.Logger

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
      logger.logInfo("\n\n~~~[LOGGING RECEIVED FROM REMOTE EXECUTION CLIENT]~~~\n\n" + rec.loggerOut + "~~~[REMOTE LOGGING FINISHED]~~~")
      logger.logInfo("\n\n~~~[ERRORS RECEIVED FROM REMOTE EXECUTION CLIENT]~~~\n\n" + rec.loggerError + "~~~[REMOTE ERROR FINISHED]~~~")
      logger.logInfo("Received response from " + hostname + ":" + port + " at " + timestampReceivedBack + ": " + rec.response.toString)
      ExecutionClientResponse(rec.timestampReceived, rec.timestampStarted, timestampReceivedBack, rec.response, rec.parsedExecutionCommand, logger.getOut(), logger.getErr())
    }
    }(using ExecutionContext.global)
  }
}
