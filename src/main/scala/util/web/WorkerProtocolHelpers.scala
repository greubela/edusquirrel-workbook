package util.web

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
