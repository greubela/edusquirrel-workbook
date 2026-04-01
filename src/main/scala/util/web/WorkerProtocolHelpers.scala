package util.web

import org.scalajs.dom

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import util.web.JsHelpers.*

final class WorkerRequestTracker {
  private var nextId = 1
  private val pending = js.Dictionary[js.Function1[js.Dynamic, Unit]]()

  def register(handler: js.Function1[js.Dynamic, Unit]): Int = {
    val id = nextId
    nextId += 1
    pending(id.toString) = handler
    id
  }

  def complete(dataAny: js.Any): Unit = {
    val data = asDynamic(dataAny)
    val id = asInt(data.selectDynamic("id").asInstanceOf[js.Any]).toString
    pending.get(id).foreach { callback =>
      pending -= id
      callback(data)
    }
  }

  def failAll(errorPayload: js.Dynamic): Unit = {
    pending.keys.foreach { id =>
      val callback = pending(id)
      pending -= id
      callback(errorPayload)
    }
  }

  def clear(): Unit =
    pending.clear()
}

object WorkerProtocolHelpers {

  def wireMessages(worker: dom.Worker, tracker: WorkerRequestTracker): Unit = {
    worker.onmessage = { (event: dom.MessageEvent) =>
      tracker.complete(event.data.asInstanceOf[js.Any])
    }
  }

  def failAllFromWorkerError(
                              tracker: WorkerRequestTracker,
                              event: dom.ErrorEvent,
                              extraErrorFields: (String, js.Any)*
                            ): Unit = {
    val errorMessage =
      s"${event.message} (${event.filename}:${event.lineno}:${event.colno})"

    tracker.failAll(
      obj(
        "ok" -> false,
        "error" -> obj(
          ("message" -> errorMessage) +: extraErrorFields *
        )
      ).asInstanceOf[js.Dynamic]
    )
  }

  def request(
               worker: dom.Worker,
               tracker: WorkerRequestTracker,
               operation: String,
               payload: js.Object,
               operationFieldName: String,
               responsePayloadFieldName: String,
               onError: js.Any => Throwable
             ): Future[js.Any] = {
    val promise = Promise[js.Any]()
    val id = tracker.register { (data: js.Dynamic) =>
      if (asBoolean(data.ok)) promise.success(data.selectDynamic(responsePayloadFieldName).asInstanceOf[js.Any])
      else promise.failure(onError(data.error.asInstanceOf[js.Any]))
    }

    worker.postMessage(
      obj(
        "id" -> id,
        operationFieldName -> operation,
        "payload" -> payload
      )
    )

    promise.future
  }
}
