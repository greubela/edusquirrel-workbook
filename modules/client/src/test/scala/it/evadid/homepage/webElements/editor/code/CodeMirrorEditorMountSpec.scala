package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import munit.FunSuite
import org.scalajs.dom
import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class CodeMirrorEditorMountSpec extends FunSuite {
  test("mount yields and sends the configured Python gutter to the facade, while unmount destroys handles") {
    val globals = js.Dynamic.global.globalThis
    val requireFn = js.Dynamic.global.selectDynamic("require")
    val root = js.Dynamic.global.process.cwd().asInstanceOf[String]
    val window = js.Dynamic.newInstance(requireFn(root + "/node_modules/jsdom").JSDOM)("<html><body></body></html>").window
    val names = List("window", "document", "Element", "Node")
    val previous = names.map(name => (name, globals.selectDynamic(name),
      js.Dynamic.global.Object.prototype.hasOwnProperty.call(globals, name).asInstanceOf[Boolean]))
    names.foreach(name => globals.updateDynamic(name)(if (name == "window") window else window.selectDynamic(name)))
    val completed = Promise[Unit]()
    var received = List.empty[Boolean]
    var destroyed = 0
    window.updateDynamic("EduSquirrelCodeMirror")(js.Dynamic.literal(
      createEditor = ((config: CodeMirrorEditor.EditorConfig) => {
        received = received :+ !js.isUndefined(config.flowchart)
        if (received.size == 3) completed.success(())
        var doc = config.doc.getOrElse("")
        js.Dynamic.literal(
          getDoc = (() => doc): js.Function0[String],
          setDoc = ((value: String) => { doc = value }): js.Function1[String, Unit],
          setDiagnostics = ((_: js.Array[js.Object]) => ()): js.Function1[js.Array[js.Object], Unit],
          focus = (() => ()): js.Function0[Unit],
          destroy = (() => { destroyed += 1 }): js.Function0[Unit]
        ).asInstanceOf[CodeMirrorEditor.CodeMirrorHandle]
      }): js.Function1[CodeMirrorEditor.EditorConfig, CodeMirrorEditor.CodeMirrorHandle]
    ))
    val mounts = List((Python, false), (Python, true), (Java, true)).map { (language, enabled) =>
      val host = dom.document.createElement("div")
      dom.document.body.appendChild(host)
      render(host, CodeMirrorEditor(Var("print(1)"), language = language,
        editorConfig = CodeMirrorEditorConfig(enabled)).getDomElement())
    }
    assertEquals(received, Nil, "even a cached facade is not mounted synchronously")
    completed.future.map { _ =>
      assertEquals(received, List(false, true, false))
    }.transform { result =>
      try mounts.foreach(_.unmount())
      finally {
        previous.foreach { (name, value, existed) =>
          if (existed) globals.updateDynamic(name)(value)
          else js.Dynamic.global.Reflect.deleteProperty(globals, name)
        }
        window.close()
      }
      assertEquals(destroyed, 3)
      result
    }
  }
}
