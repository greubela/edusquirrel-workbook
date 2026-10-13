package it.evadid.homepage.webElements.editor.code

import munit.FunSuite
import scala.scalajs.js

class CodeMirrorEditorPreheatSpec extends FunSuite {
  test("startup warmup is scheduled once at idle priority and never creates an editor") {
    val globals = js.Dynamic.global.globalThis
    val previous = js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, "window")
    var callbacks = List.empty[js.Function1[js.Any, Unit]]
    var deadlines = List.empty[Int]
    var creations = 0
    val idle: js.Function2[js.Function1[js.Any, Unit], js.Dynamic, Int] = (callback, options) => {
      callbacks = callbacks :+ callback
      deadlines = deadlines :+ options.timeout.asInstanceOf[Int]
      callbacks.size
    }
    val create: js.Function0[Unit] = () => { creations += 1 }
    globals.updateDynamic("window")(js.Dynamic.literal(
      requestIdleCallback = idle,
      EduSquirrelCodeMirror = js.Dynamic.literal(createEditor = create)
    ))
    try {
      CodeMirrorEditorLoading.preheat()
      CodeMirrorEditorLoading.preheat()
      assertEquals(callbacks.size, 1)
      assertEquals(deadlines, List(2000))
      assertEquals(creations, 0)
      callbacks.head(js.undefined)
      assertEquals(creations, 0)
    } finally {
      if js.isUndefined(previous) then js.special.delete(globals, "window")
      else js.Dynamic.global.Object.defineProperty(globals, "window", previous)
    }
  }
}
