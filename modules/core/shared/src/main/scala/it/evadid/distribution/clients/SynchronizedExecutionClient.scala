package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.util.logging.Logger

import java.time.LocalDateTime
import scala.collection.mutable
import scala.concurrent.*
import scala.util.{Failure, Success}

private case class SynchronizedExecutionClient(baseHandler: ExecutionClient, ec: ExecutionContext) extends ExecutionClient {

  case class QueuedCommand(command: ExecutionCommand, promise: Promise[ExecutionClientResponse], timeReceived: LocalDateTime, logger: Logger)

  private val queue = mutable.Queue.empty[QueuedCommand]
  private var running = false

  override private[clients] def handleCommandExecution(logger: Logger, executionCommand: ExecutionCommand): Future[ExecutionClientResponse] = queue.synchronized {
    val promise = Promise[ExecutionClientResponse]()
    queue.enqueue(QueuedCommand(executionCommand, promise, LocalDateTime.now(), logger))
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
      baseHandler.handleCommandExecution(queuedCommand.logger, queuedCommand.command).onComplete {
        case Success(res) => {
          queuedCommand.promise.success(res.fixReceived(queuedCommand.timeReceived))
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

}
