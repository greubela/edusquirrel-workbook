package it.evadid.distribution.clients

import it.evadid.distribution.ExecutionCommand
import it.evadid.distribution.ExecutionCommand.{CommandHistory, ExecutionInfo, ExecutionResult}
import it.evadid.distribution.executor.Executor

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.*

trait LocalExecutionClient extends ExecutionClient {


  def handlers: List[Executor]

  protected def executeWithFirstHandler(executionCommand: ExecutionCommand): ExecutionInfo = {
    val useHandler = handlers.find(_.canExecute(executionCommand))
    if (useHandler.isEmpty) {
      val err = new IllegalArgumentException(s"Command '${executionCommand.name}' did not have a suitable executor (${handlers.size} executors)")
      ExecutionInfo(executionCommand, Failure(err), None)
    } else {
      useHandler.get.execute(executionCommand) match {
        case Success(res) => res
        case Failure(cause) => {
          val err = new IllegalArgumentException(s"Execution of command failed because of: ", cause)
          ExecutionInfo(executionCommand, Failure(err), None)
        }
      }
    }
  }


  lazy val makeSynchronized: LocalExecutionClient = LocalExecutionClient.SynchronizedExecution(this, ExecutionContext.global)

}

object LocalExecutionClient {

  private case class SynchronizedExecution(baseHandler: LocalExecutionClient, ec: ExecutionContext) extends LocalExecutionClient {

    private val queue = mutable.Queue.empty[(ExecutionCommand, Promise[ExecutionInfo])]
    private var running = false

    val handlers: List[Executor] = baseHandler.handlers

    override def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionInfo] = synchronized {
      val promise = Promise[ExecutionInfo]()
      queue.enqueue((executionCommand, promise))
      ensureRunning()
      promise.future
    }


    private def ensureRunning(): Unit = synchronized {
      if (!running && queue.nonEmpty) {
        val (command, promise) = queue.dequeue()
        running = true
        baseHandler.executeCommand(command).onComplete { result => handleOnComplete(result, promise) }(using ec)
      }
    }

    private def handleOnComplete(result: Try[ExecutionInfo], promise: Promise[ExecutionInfo]): Unit = synchronized {
      promise.tryComplete(result)
      running = false
      ensureRunning()
    }

  }

}
