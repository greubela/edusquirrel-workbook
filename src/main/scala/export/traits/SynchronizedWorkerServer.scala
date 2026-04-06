package `export`.traits

import `export`.traits.WorkerTraits.WorkerCommand

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Abstract worker server that guarantees single-command execution.
 *
 * Incoming commands are enqueued and processed in FIFO order.
 * The server intentionally does not apply any timeout policy.
 */
abstract class SynchronizedWorkerServer(
                                         using ec: ExecutionContext
                                       ) extends AbstractWorkerServer {

  private val queue = mutable.Queue.empty[(WorkerCommand, Promise[Map[String, String]])]
  private var running = false

  override protected final def execute(workerCommand: WorkerCommand): Future[Map[String, String]] = synchronized {
    val promise = Promise[Map[String, String]]()
    queue.enqueue((workerCommand, promise))
    runNext()
    promise.future
  }

  private def runNext(): Unit = synchronized {
    if (!running && queue.nonEmpty) {
      val (command, promise) = queue.dequeue()
      running = true

      handleTask(command).onComplete { result =>
        promise.tryComplete(result)
        synchronized {
          running = false
          runNext()
        }
      }
    }
  }
}
