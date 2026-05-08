package it.evadid.distribution.clients

import it.evadid.distribution.command.*
import it.evadid.util.Logger

import java.time.LocalDateTime
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

private case class SynchronizedExecutionClient(baseHandler: ExecutionClient, ec: ExecutionContext) extends ExecutionClient {

  private val queue = mutable.Queue.empty[(ExecutionCommand, Promise[ExecutionInfo], LocalDateTime, Logger)]
  private var running = false

  override def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[ExecutionInfo] = synchronized {
    val promise = Promise[ExecutionInfo]()
    queue.enqueue((executionCommand, promise, LocalDateTime.now(), logger))
    ensureRunning()
    promise.future
  }

  private def ensureRunning(): Unit = synchronized {
    if (!running && queue.nonEmpty) {
      val (command, promise, timeRequested, logger) = queue.dequeue()
      running = true
      baseHandler.handleExecution(command, logger).onComplete(resTry => handleOnComplete(resTry, promise, timeRequested))(using ec)
    }
  }

  private def handleOnComplete(result: Try[ExecutionInfo], promise: Promise[ExecutionInfo], executionRequested: LocalDateTime): Unit = synchronized {
    val fixedTime = result.map(_.fixTime(executionRequested, executionRequested))
    promise.tryComplete(fixedTime)
    running = false
    ensureRunning()
  }

  override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = baseHandler.canExecuteCommand(executionCommand)
}
