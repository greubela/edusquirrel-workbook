package it.evadid.homepage.control.startup

import munit.FunSuite
import scala.concurrent.Promise
import scala.scalajs.js

class StartupTasksSpec extends FunSuite {
  test("phase two waits for an animation frame and a following browser task") {
    val globals = js.Dynamic.global.globalThis
    val previous = js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, "window")
    var frame: Option[js.Function1[Double, Unit]] = None
    val request: js.Function1[js.Function1[Double, Unit], Int] = callback => { frame = Some(callback); 1 }
    globals.updateDynamic("window")(js.Dynamic.literal(requestAnimationFrame = request))
    val done = Promise[Unit]()
    StartupTasks.afterPaint { done.success(()); () }
    assert(!done.isCompleted)
    frame.get(0)
    assert(!done.isCompleted, "work must not run inside the animation frame callback")
    if js.isUndefined(previous) then js.special.delete(globals, "window")
    else js.Dynamic.global.Object.defineProperty(globals, "window", previous)
    done.future
  }
}
