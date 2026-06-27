package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.Logger

import java.time.LocalDateTime
import scala.collection.mutable
import scala.concurrent.*
import scala.util.{Failure, Success}

private case class SynchronizedExecutionClient(baseHandler: ExecutionClient, ec: ExecutionContext) extends ExecutionClient {

  case class QueuedCommand(command: ExecutionCommand, promise: Promise[ExecutionClientResponse], timeReceived: LocalDateTime, logger: Logger)

  private val queue = mutable.Queue.empty[QueuedCommand]
  private var running = false

  override def handleExecution(executionCommand: ExecutionCommand): Future[ExecutionClientResponse] = queue.synchronized {
    val promise = Promise[ExecutionClientResponse]()
    queue.enqueue(QueuedCommand(executionCommand, promise, LocalDateTime.now(), Logger()))
    ensureRunning()
    promise.future
  }

  private def ensureRunning(): Unit = queue.synchronized {

    def afterFinished(): Unit = {
      running = false
      ensureRunning()
    }

    if (!running && queue.nonEmpty) {
      val queuedCommand: QueuedCommand = queue.dequeue()
      running = true
      val timestampStarted: LocalDateTime = LocalDateTime.now()
      queuedCommand.logger.logInfo(s"ExecutionClient: start to execute command ${queuedCommand.command.name} at ${timestampStarted}")
      baseHandler.handleExecutionRaw(queuedCommand.command, queuedCommand.logger).onComplete {
        case Success(resMap) => {
          val result: ExecutionClientResponse = ExecutionClientResponse(queuedCommand.timeReceived, timestampStarted, LocalDateTime.now(), Right(resMap), Some(queuedCommand.command), queuedCommand.logger.getOut(), queuedCommand.logger.getErr())
          queuedCommand.promise.success(result)
          afterFinished()
        }
        case Failure(err) => {
          val errMsg: String = s"ExecutionClient: failed to execute command ${queuedCommand.command.name} at ${timestampStarted}!: ${err.getMessage}"
          queuedCommand.logger.logError(errMsg)
          queuedCommand.promise.failure(Exception(errMsg, err))
          afterFinished()
        }
      }(using ExecutionContext.global)

    }
  }

  override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = baseHandler.canExecuteCommand(executionCommand)

  override private[distribution] def handleExecutionRaw(executionCommand: ExecutionCommand, logger: Logger): Future[Map[String, String]] = baseHandler.handleExecutionRaw(executionCommand, logger)
}
