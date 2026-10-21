package it.evadid.homepage.webElements.facades

import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import com.raquo.laminar.api.L.*
import it.evadid.facades.CodeMirror
import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class CodeMirrorEditorPreheatSpec extends EditorDomSuite {
  test("warmup does not create editors and an available library is reused") {
    val global = js.Dynamic.global.globalThis
    val previous = global.selectDynamic("EduSquirrelCodeMirrorLibraries")
    global.updateDynamic("EduSquirrelCodeMirrorLibraries")(js.Dynamic.literal())
    CodeMirror.preload()
    CodeMirror.preload()
    val ready = Promise[Unit]()
    setTimeout(300) { ready.success(()) }
    ready.future.flatMap(_ => CodeMirror.preload()).map { _ =>
      assertEquals(org.scalajs.dom.document.querySelectorAll(".cm-editor").length, 0)
    }.andThen { case _ =>
      global.updateDynamic("EduSquirrelCodeMirrorLibraries")(previous)
    }
  }
}
