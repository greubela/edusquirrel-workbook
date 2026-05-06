package it.evadid.distribution.clients

import it.evadid.distribution.ExecutionCommand
import org.scalajs.dom
import upickle.default.{read, write}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}

trait WorkerLike {
  def postMessage(message: String): Unit
  var onmessage: dom.MessageEvent => Unit
  var onerror: dom.ErrorEvent => Any
}

private class DomWorkerAdapter(path: String) extends WorkerLike {
  private val worker = new dom.Worker(path)

  override def postMessage(message: String): Unit = worker.postMessage(message)

  override def onmessage_=(handler: dom.MessageEvent => Unit): Unit =
    worker.onmessage = handler

  override def onmessage: dom.MessageEvent => Unit = worker.onmessage

  override def onerror_=(handler: dom.ErrorEvent => Any): Unit =
    worker.onerror = handler

  override def onerror: dom.ErrorEvent => Any = worker.onerror
}

object WorkerExecution {
  def apply(workerScriptPath: String): WorkerExecution =
    new WorkerExecution(new DomWorkerAdapter(workerScriptPath))
}

class WorkerExecution private[clients](worker: WorkerLike) extends ExecutionClient {

  private val pending = mutable.Map.empty[String, Promise[ExecutionCommand.ExecutionInfo]]

  worker.onmessage = (event: dom.MessageEvent) => {
    event.data match {
      case text: String =>
        val payload = read[Map[String, String]](text)
        val requestId = payload.getOrElse("requestId",
          throw new IllegalStateException("Worker response is missing 'requestId' field")
        )
        val executionInfoJson = payload.getOrElse("executionInfo",
          throw new IllegalStateException("Worker response is missing 'executionInfo' field")
        )
        pending.remove(requestId) match {
          case Some(promise) => promise.success(read[ExecutionCommand.ExecutionInfo](executionInfoJson))
          case None =>
        }
      case _ =>
    }
  }

  worker.onerror = (event: dom.ErrorEvent) =>
    val exception = new RuntimeException(s"Worker error at ${event.filename}:${event.lineno}: ${event.message}")
    pending.values.foreach(_.tryFailure(exception))
    pending.clear()

  override def executeCommand(executionCommand: ExecutionCommand): Future[ExecutionCommand.ExecutionInfo] = {
    val requestId = java.util.UUID.randomUUID().toString
    val promise = Promise[ExecutionCommand.ExecutionInfo]()
    pending.put(requestId, promise)
    val payload = write(Map(
      "requestId" -> requestId,
      "command" -> write(executionCommand)
    ))
    worker.postMessage(payload)
    promise.future
  }
}
