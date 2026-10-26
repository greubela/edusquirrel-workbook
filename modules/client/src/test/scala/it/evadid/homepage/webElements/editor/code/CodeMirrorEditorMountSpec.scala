package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.facades.CodeMirror
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import munit.FunSuite
import org.scalajs.dom

import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class CodeMirrorEditorMountSpec extends FunSuite {
  test("mount yields, updates gutter numbering without rebuilding and releases native handles") {
    val globals = js.Dynamic.global.globalThis
    val requireFn = js.Dynamic.global.selectDynamic("require")
    val root = js.Dynamic.global.process.cwd().asInstanceOf[String]
    val window = js.Dynamic.newInstance(requireFn(root + "/node_modules/jsdom").JSDOM)("<html><body></body></html>").window
    val names = List("window", "document", "Element", "Node")
    val previous = names.map(name => (name, globals.selectDynamic(name),
      js.Dynamic.global.Object.prototype.hasOwnProperty.call(globals, name).asInstanceOf[Boolean]))
    names.foreach(name => globals.updateDynamic(name)(if (name == "window") window else window.selectDynamic(name)))
    val completed = Promise[Unit]()
    val remounted = Promise[Unit]()
    val renumbered = Promise[Unit]()
    val firstLine = Var(7)
    val rebuild = Var(0)
    val received = Var(List.empty[Boolean])
    val initialLines = Var(List.empty[Int])
    val lineUpdates = Var(Vector.empty[Int])
    val diagnosticRanges = Var(Vector.empty[(Int, Int, Int, Int)])
    val documentWrites = Var(0)
    val destroyed = Var(0)
    val createEditor: CodeMirror.EditorConfig => scala.concurrent.Future[CodeMirror.CodeMirrorHandle] = config =>
      scala.concurrent.Future.successful {
        received.update(_ :+ config.flowchart.nonEmpty)
        initialLines.update(_ :+ config.firstLineNumber)
        if (received.now().size == 3) completed.success(())
        if (received.now().size == 4) remounted.success(())
        val doc = Var(config.doc)
        js.Dynamic.literal(
          getDoc = (() => doc.now()): js.Function0[String],
          setDoc = ((value: String) => { documentWrites.update(_ + 1); doc.set(value) }): js.Function1[String, Unit],
          setDiagnostics = ((values: js.Array[js.Object]) => if config.language == "java" then
            diagnosticRanges.set(values.toVector.map { value =>
              val diagnostic = value.asInstanceOf[js.Dynamic]
              (diagnostic.line.asInstanceOf[Int], diagnostic.endLine.asInstanceOf[Int],
                diagnostic.fromCh.asInstanceOf[Int], diagnostic.toCh.asInstanceOf[Int])
            })): js.Function1[js.Array[js.Object], Unit],
          setFirstLineNumber = ((value: Int) => if config.language == "java" then {
            lineUpdates.update(_ :+ value)
            if value == 15 then renumbered.trySuccess(())
            ()
          }): js.Function1[Int, Unit],
          focus = (() => ()): js.Function0[Unit],
          destroy = (() => { destroyed.update(_ + 1) }): js.Function0[Unit]
        ).asInstanceOf[CodeMirror.CodeMirrorHandle]
      }
    val mounts = List((Python, false), (Python, true), (Java, true)).map { (language, enabled) =>
      val host = dom.document.createElement("div")
      dom.document.body.appendChild(host)
      val editor = CodeMirrorEditor(it.evadid.util.logging.Logger.withNameAndPrefixes(Some("test"), it.evadid.util.logging.derived.PrintToStdLogger.printNothing),
        Var(if language == Java then "int value = 1;\nvalue++;" else "print(1)"), language = language,
        config = Var(CodeMirrorEditorConfig(enabled)), createEditor = createEditor,
        diagnostics = Val(if language == Java then Seq(CodeMirrorEditor.Diagnostic(2, Some(2), Some(0), Some(5))) else Nil),
        firstLineNumber = if language == Java then firstLine.signal else Val(1))
      render(host, if language == Python && !enabled then div(child <-- rebuild.signal.map(_ => editor.getDomElement()))
        else editor.getDomElement())
    }
    assertEquals(received.now(), Nil, "even a cached facade is not mounted synchronously")
    completed.future.flatMap { _ =>
      assertEquals(received.now(), List(false, true, false))
      assertEquals(initialLines.now(), List(1, 1, 7))
      firstLine.set(15)
      renumbered.future.flatMap { _ =>
        assertEquals(lineUpdates.now().last, 15)
        assertEquals(documentWrites.now(), 0)
        assertEquals(diagnosticRanges.now(), Vector((2, 2, 0, 5)))
        assertEquals(received.now().size, 3)
        assertEquals(destroyed.now(), 0)
        rebuild.update(_ + 1)
        remounted.future.map { _ =>
          assertEquals(received.now(), List(false, true, false, false))
          assertEquals(destroyed.now(), 1)
        }
      }
    }.transform { result =>
      try mounts.foreach(_.unmount())
      finally {
        previous.foreach { (name, value, existed) =>
          if (existed) globals.updateDynamic(name)(value)
          else js.Dynamic.global.Reflect.deleteProperty(globals, name)
        }
        window.close()
      }
      assertEquals(destroyed.now(), 4)
      result
    }
  }
}
