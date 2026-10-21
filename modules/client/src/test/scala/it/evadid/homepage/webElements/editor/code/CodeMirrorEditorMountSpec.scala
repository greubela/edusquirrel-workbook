package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
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
    val remounted = Promise[Unit]()
    val rebuild = Var(0)
    val received = Var(List.empty[Boolean])
    val destroyed = Var(0)
    window.updateDynamic("EduSquirrelCodeMirror")(js.Dynamic.literal(
      createEditor = ((config: it.evadid.homepage.webElements.facades.CodeMirror.EditorConfig) => {
        received.update(_ :+ !js.isUndefined(config.flowchart))
        if (received.now().size == 3) completed.success(())
        if (received.now().size == 4) remounted.success(())
        val doc = Var(config.doc.getOrElse(""))
        js.Dynamic.literal(
          getDoc = (() => doc.now()): js.Function0[String],
          setDoc = ((value: String) => { doc.set(value) }): js.Function1[String, Unit],
          setDiagnostics = ((_: js.Array[js.Object]) => ()): js.Function1[js.Array[js.Object], Unit],
          focus = (() => ()): js.Function0[Unit],
          destroy = (() => { destroyed.update(_ + 1) }): js.Function0[Unit]
        ).asInstanceOf[it.evadid.homepage.webElements.facades.CodeMirror.CodeMirrorHandle]
      }): js.Function1[it.evadid.homepage.webElements.facades.CodeMirror.EditorConfig, it.evadid.homepage.webElements.facades.CodeMirror.CodeMirrorHandle]
    ))
    val mounts = List((Python, false), (Python, true), (Java, true)).map { (language, enabled) =>
      val host = dom.document.createElement("div")
      dom.document.body.appendChild(host)
      val editor = CodeMirrorEditor(it.evadid.util.logging.Logger.withNameAndPrefixes(Some("test"), it.evadid.util.logging.derived.PrintToStdLogger.printNothing), Var("print(1)"), language = language,
        config = Var(CodeMirrorEditorConfig(enabled)))
      render(host, if language == Python && !enabled then div(child <-- rebuild.signal.map(_ => editor.getDomElement()))
        else editor.getDomElement())
    }
    assertEquals(received.now(), Nil, "even a cached facade is not mounted synchronously")
    completed.future.flatMap { _ =>
      assertEquals(received.now(), List(false, true, false))
      rebuild.update(_ + 1)
      remounted.future.map { _ =>
        assertEquals(received.now(), List(false, true, false, false))
        assertEquals(destroyed.now(), 1)
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
