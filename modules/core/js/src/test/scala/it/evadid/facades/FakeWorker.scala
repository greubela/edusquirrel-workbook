package it.evadid.facades

import scala.scalajs.js
import com.raquo.airstream.state.Var

/** Minimal model of the browser `Worker` contract: constructor arguments, posted messages and the event handlers. */
private[evadid] class FakeWorker(val url: String, val options: js.UndefOr[js.Dynamic]) extends js.Object {
  val posted: js.Array[js.Any] = js.Array()
  var terminated: Boolean = false
  var onmessage: js.Any = js.undefined
  var onerror: js.Any = js.undefined
  var onmessageerror: js.Any = js.undefined
  FakeWorker.created.push(this)
  if FakeWorker.failConstruction.now() then throw js.JavaScriptException(js.Error("Workers are disabled"))

  def postMessage(message: js.Any): Unit = posted.push(message)
  def terminate(): Unit = terminated = true

  private def fire(handler: js.Any, event: js.Object): Unit = handler.asInstanceOf[js.Function1[js.Object, Unit]](event)
  def deliver(data: js.Any): Unit = fire(onmessage, js.Dynamic.literal(data = data))
  def crash(message: String, filename: String, line: Int): Unit =
    fire(onerror, js.Dynamic.literal(message = message, filename = filename, lineno = line))
  def undecodable(): Unit = fire(onmessageerror, js.Dynamic.literal())
}

private[evadid] object FakeWorker {
  val created: js.Array[FakeWorker] = js.Array()
  val failConstruction: Var[Boolean] = Var(false)

  /** Installs the fake as the global `Worker` for a test body and restores the previous global afterwards. */
  def installed[A](body: => scala.concurrent.Future[A]): scala.concurrent.Future[A] = {
    val globals = js.Dynamic.global.globalThis
    val previous = js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, "Worker")
    def restore(): Unit = {
      failConstruction.set(false)
      created.length = 0
      if js.isUndefined(previous) then js.special.delete(globals, "Worker")
      else js.Dynamic.global.Object.defineProperty(globals, "Worker", previous)
    }
    globals.updateDynamic("Worker")(js.constructorOf[FakeWorker])
    val result = try body catch { case error: Throwable => restore(); throw error }
    result.andThen(_ => restore())(using scala.scalajs.concurrent.JSExecutionContext.queue)
  }
}
