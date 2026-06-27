package it.evadid.distribution.clients

import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState}
import it.evadid.core.datastructures.state.async.AsyncDataState.{AsyncDataFailed, AsyncDataStateFinished}
import it.evadid.distribution.command.*
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoUntyped
import it.evadid.util.Logger

import java.time.LocalDateTime
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

private case class SynchronizedExecutionClient(baseHandler: ExecutionClient,  ec: ExecutionContext) extends ExecutionClient {

  private val queue = mutable.Queue.empty[(ExecutionCommand, Promise[AsyncDataStateFinished[Nothing, ExecutionInfo]], LocalDateTime, Logger)]
  private var running = false



  override def handleExecution(executionCommand: ExecutionCommand, logger: Logger): Future[AsyncDataStateFinished[Nothing, ExecutionInfo]] = synchronized {
    val promise = Promise[AsyncDataStateFinished[Nothing, ExecutionInfo]]()
    queue.enqueue((executionCommand, promise, LocalDateTime.now(), logger))
    ensureRunning()
    promise.future
  }

  private def ensureRunning(): Unit = synchronized {
    if (!running && queue.nonEmpty) {
      val (command, promise, timeRequested, logger) = queue.dequeue()
      running = true
      baseHandler.handleExecution(command, logger).onComplete{
        case Success(state) => {
          handleOnComplete(state, promise, timeRequested)
        }
        case Failure(err) => {
          val exception = SerializedException(s"ExecutionClient: failed to execute command ${command.name}", err)
          handleOnComplete(AsyncDataFailed(exception, None), promise, timeRequested)
        }
      }(using ec)
    }
  }

  private def handleOnComplete(result: AsyncDataStateFinished[Nothing, ExecutionInfo], promise: Promise[AsyncDataStateFinished[Nothing, ExecutionInfo]], executionRequested: LocalDateTime): Unit = synchronized {
    val fixedTime: AsyncDataStateFinished[Nothing, ExecutionInfo] = result.mapFinished(_.withFixedTime(executionRequested, executionRequested))
    promise.success(fixedTime)
    running = false
    ensureRunning()
  }

  override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = baseHandler.canExecuteCommand(executionCommand)
}
