package `export`.traits

import `export`.traits.WorkerTraits.*
import org.scalajs.dom
import util.web.JsHelpers

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.timers.*

object AbstractWorkerClient {

  def apply(exportedName: String): AbstractWorkerClient = ??? // todo

}

abstract class AbstractWorkerClient(
                                     protected val worker: dom.Worker
                                   ) {

  initMessageHandling()

  private val pendingTasks = mutable.Map.empty[String, PendingTask]

  final def enqueue(
                     workerCommand: WorkerCommand
                   ): Future[ExecutionResult] = {

    val id = UUID.randomUUID().toString
    val promise = Promise[ExecutionResult]()
    pendingTasks.put(id, PendingTask(id, promise, java.time.LocalDateTime.now()))
   //worker.postMessage(WorkerWire.request(id, workerCommand.name, workerCommand.params))
    // todo
    promise.future
  }

  final def terminate(): Unit = {
    pendingTasks.values.foreach { p =>
      p.promise.tryFailure(
        new IllegalStateException("Worker terminated before reply")
      )
    }
    pendingTasks.clear()
    worker.terminate()
  }


  private def initMessageHandling(): Unit = {
    worker.onmessage = { (e: dom.MessageEvent) =>
      val msg = e.data.asInstanceOf[js.Dynamic]
      val kind = msg.kind.asInstanceOf[String]

      if (kind == "response") {
        val id = msg.id.asInstanceOf[String]
        pendingTasks.remove(id).foreach { p =>
          if (JsHelpers.parseOrElse[Boolean](msg.ok, true)) {
            val data = JsHelpers.stringMapHelper.fromJsToScala(msg.data)
            // todo
          } else {
            val error = JsHelpers.parseOrElse[String](msg.error, "Unknown worker error")
            p.promise.tryFailure(new RuntimeException(error))
          }
        }
      }
    }

    worker.onerror = { (e: dom.ErrorEvent) =>
      val ex = new RuntimeException(
        s"Worker error: ${Option(e.message).getOrElse("unknown error")}"
      )
      pendingTasks.values.foreach { p =>
        p.promise.tryFailure(ex)
      }
      pendingTasks.clear()
    }
  }
}
