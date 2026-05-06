package it.evadid.distribution.clients

import it.evadid.distribution.ExecutionCommand.ExecutionInfo
import it.evadid.distribution.{ExecutionClient, ExecutionCommand, Executor}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Execution client that serializes command handling for a given executor.
 *
 * Commands are enqueued and processed in FIFO order with single-flight execution.
 */
case class SynchronizedExecution(commandHandler: Executor, ec: ExecutionContext = ExecutionContext.global) extends ExecutionClient {

  private val queue = mutable.Queue.empty[(ExecutionCommand, Promise[ExecutionInfo])]
  private var running = false

  override def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionInfo] = synchronized {
    val promise = Promise[ExecutionInfo]()
    queue.enqueue((executionCommand, promise))
    runNext()
    promise.future
  }

  private def runNext(): Unit = synchronized {
    if (!running && queue.nonEmpty) {
      val (command, promise) = queue.dequeue()
      running = true

      Future(commandHandler.forceExecution(command))(using ec).onComplete { result =>
        promise.tryComplete(result)
        synchronized {
          running = false
          runNext()
        }
      }(using ec)
    }
  }
}
