package it.evadid.homepage.control.startup

import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout
import scala.util.Try

/** Yield a paint opportunity before starting optional work. Never use a microtask for this. */
object StartupTasks {
  def afterPaint(work: => Unit): Unit =
    dom.window.requestAnimationFrame(_ => { setTimeout(0)(work); () })

  def idle[A](work: => A): Future[A] = {
    val result = Promise[A]()
    val callback: js.Function1[js.Any, Unit] = _ => { result.complete(Try(work)); () }
    val window = dom.window.asInstanceOf[js.Dynamic]
    if (js.typeOf(window.selectDynamic("requestIdleCallback")) == "function")
      window.requestIdleCallback(callback, js.Dynamic.literal(timeout = 2000))
    else setTimeout(250) { callback(js.undefined) }
    result.future
  }
}
