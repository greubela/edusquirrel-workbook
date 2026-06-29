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

  protected def sendTo(logger: Logger, hostname: String, port: Int, command: ExecutionCommand): Future[Map[String, String]]

  override def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionClientResponse] = {
    val res: Future[Map[String, String]] = sendTo(logger, hostname, port, executionCommand)
    res.map { responseData => {
      val timestampReceivedBack: LocalDateTime = LocalDateTime.now()
      logger.logInfo("Received response from " + hostname + ":" + port + " at " + timestampReceivedBack + ", now parsing responseData!")

      try {
        val rec = ExecutionClientResponse.parseFromDefaultMapAndUpdateLogger(logger, responseData)
        ExecutionClientResponse(rec.timestampReceived, rec.timestampStarted, timestampReceivedBack, rec.response, rec.parsedExecutionCommand, logger.getOut(), logger.getErr())
      } catch case e: Throwable => {
        logger.logWarn(s"Malformed responseData:\n    ${responseData.iterator.map(tup => tup._1 + " -> " + tup._2).mkString("\n    ")}")
        val err = Exception(s"Could not parse ExecutionClientResponse from responseData: ${e.getMessage}", e)
        throw err
      }
    }
    }(using ExecutionContext.global)
  }
}
