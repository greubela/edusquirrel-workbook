package it.evadid.facades

import it.evadid.facades.CodeMirror.EditorConfig
import it.evadid.facades.internal.CodeMirrorRuntime
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class CodeMirrorSpec extends CodeMirrorSuite {
  private val globals = js.Dynamic.global.globalThis

  test("editors are only created once the CodeMirror libraries are available") {
    restoringGlobals("EduSquirrelCodeMirrorLibraries") {
      js.special.delete(globals, "EduSquirrelCodeMirrorLibraries")
      assert(!CodeMirror.Api.available)
      assert(intercept[IllegalStateException](CodeMirrorRuntime.createEditor(EditorConfig(host())))
        .getMessage.contains("not loaded"))
      assert(intercept[IllegalArgumentException](CodeMirror.editor(EditorConfig(host())).createLoadedInstance())
        .getMessage.contains("not been preloaded"))
      globals.EduSquirrelCodeMirrorLibraries = null
      assert(!CodeMirror.Api.available)
      Future.unit
    }
  }

  test("failed module imports fail every consumer until the libraries become available") {
    // The Node test runner rejects dynamic imports, which models an unreachable module CDN.
    restoringGlobals("EduSquirrelCodeMirrorLibraries") {
      js.special.delete(globals, "EduSquirrelCodeMirrorLibraries")
      CodeMirror.preload().failed.flatMap(_ => tick()).flatMap { _ =>
        CodeMirror.preload().failed.zip(CodeMirror.loadJava().failed)
          .zip(CodeMirror.editor(EditorConfig(host())).createInstance().failed)
      }.flatMap { _ =>
        assert(!CodeMirror.Api.available)
        CodeMirrorSuite.loaded(CodeMirrorSuite.packages).flatMap { libraries =>
          globals.EduSquirrelCodeMirrorLibraries = libraries
          CodeMirror.preload()
        }.map(_ => assert(CodeMirror.Api.available))
      }
    }
  }

  test("loaded libraries are reused without importing again and create views and editors") {
    withLibraries { libraries =>
      for {
        _ <- CodeMirror.preload()
        view <- CodeMirror.view(js.Dynamic.literal(doc = "x = 1", parent = host())).createInstance()
        handle <- CodeMirror.editor(EditorConfig(host(), doc = "y = 2")).createInstance()
      } yield {
        assertEquals(view.state.doc.toString(), "x = 1")
        assert(view.asInstanceOf[js.Dynamic].constructor eq libraries.EditorView)
        assertEquals(handle.getDoc(), "y = 2")
        val editorView = libraries.EditorView.findFromDOM(dom.document.querySelectorAll(".cm-editor").toList.last)
        editorView.dispatch(js.Dynamic.literal(changes = js.Dynamic.literal(from = 0, insert = "#")))
        assertEquals(handle.getDoc(), "#y = 2", "editors without a change callback still accept edits")
        view.destroy()
        handle.destroy()
      }
    }
  }

  test("background work runs after the current task and reports its outcome") {
    val order = js.Array[String]()
    val done = CodeMirror.inBackground { order.push("background"); 42 }
    order.push("caller")
    val failed = CodeMirror.inBackground[Int](throw IllegalStateException("parse failed"))
    done.zip(failed.failed).map { (value, error) =>
      assertEquals(order.toList, List("caller", "background"))
      assertEquals(value, 42)
      assertEquals(error.getMessage, "parse failed")
    }
  }

  test("the JavaScript facade is published for page scripts") {
    restoringGlobals("EduSquirrelCodeMirror") {
      CodeMirrorRuntime.publish()
      assert(globals.EduSquirrelCodeMirror eq CodeMirrorRuntime.facade)
      assertEquals(js.typeOf(globals.EduSquirrelCodeMirror.createEditor), "function")
      Future.unit
    }
  }
}
