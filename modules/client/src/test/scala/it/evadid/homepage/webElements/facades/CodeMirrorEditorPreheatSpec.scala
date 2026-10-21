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
    val creations = Var(0)
    val create: js.Function0[Unit] = () => creations.update(_ + 1)
    org.scalajs.dom.window.asInstanceOf[js.Dynamic].updateDynamic("EduSquirrelCodeMirror")(
      js.Dynamic.literal(createEditor = create))
    CodeMirror.preload()
    CodeMirror.preload()
    val ready = Promise[Unit]()
    setTimeout(300) { ready.success(()) }
    ready.future.flatMap(_ => CodeMirror.preload()).map { _ =>
      assertEquals(creations.now(), 0)
    }
  }
}
