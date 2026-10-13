package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaString}
import munit.FunSuite
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.setTimeout
import scala.util.Try

class CodeMirrorEditorMountSpec extends FunSuite {
  private final class ControlledMounts {
    private val globals = js.Dynamic.global.globalThis
    private val requireFn = js.Dynamic.global.selectDynamic("require")
    private val projectRoot = js.Dynamic.global.process.cwd().asInstanceOf[String]
    private val window = js.Dynamic.newInstance(requireFn(projectRoot + "/node_modules/jsdom").JSDOM)("<html><body></body></html>").window
    private val names = List("window", "document", "Element", "Node", "Event", "HTMLElement",
      "HTMLInputElement", "HTMLTextAreaElement", "HTMLSelectElement")
    private val previous = names.map(name => (name, globals.selectDynamic(name),
      js.Dynamic.global.Object.prototype.hasOwnProperty.call(globals, name).asInstanceOf[Boolean]))
    names.foreach(name => globals.updateDynamic(name)(if (name == "window") window else window.selectDynamic(name)))
    private var cleanup = List.empty[() => Unit]
    private var pending = Vector.empty[js.Function1[js.Any, Unit]]
    var created = Vector.empty[Created]

    window.updateDynamic("requestIdleCallback")(((callback: js.Function1[js.Any, Unit], _: js.Any) => {
      pending :+= callback
      pending.size
    }): js.Function2[js.Function1[js.Any, Unit], js.Any, Int])
    window.updateDynamic("EduSquirrelCodeMirror")(js.Dynamic.literal(
      createEditor = ((config: CodeMirrorEditor.EditorConfig) => {
        val current = new Created(config)
        created :+= current
        current.handle
      }): js.Function1[CodeMirrorEditor.EditorConfig, CodeMirrorEditor.CodeMirrorHandle]
    ))

    final class Created(val config: CodeMirrorEditor.EditorConfig) {
      var doc = config.doc.getOrElse("")
      var destroys = 0
      var focuses = 0
      var diagnosticCalls = Vector.empty[Vector[String]]
      private val node = dom.document.createElement("div")
      node.setAttribute("class", "cm-content")
      node.textContent = doc
      config.parent.appendChild(node)
      val handle = js.Dynamic.literal(
        getDoc = (() => doc): js.Function0[String],
        setDoc = ((value: String) => { doc = value; node.textContent = value; config.onDocChange(value) }): js.Function1[String, Unit],
        setDiagnostics = ((values: js.Array[js.Object]) => {
          diagnosticCalls :+= Vector.tabulate(values.length)(index => values(index).asInstanceOf[js.Dynamic].message.asInstanceOf[String])
        }): js.Function1[js.Array[js.Object], Unit],
        focus = (() => { focuses += 1 }): js.Function0[Unit],
        destroy = (() => { destroys += 1; node.remove() }): js.Function0[Unit]
      ).asInstanceOf[CodeMirrorEditor.CodeMirrorHandle]
      def input(value: String): Unit = { doc = value; node.textContent = value; config.onDocChange(value) }
    }

    def mount(element: Element): () => Unit = {
      val host = dom.document.createElement("div")
      dom.document.body.appendChild(host)
      val mounted = render(host, element)
      var attached = true
      val stop = () => if attached then {
        attached = false
        mounted.unmount()
        host.remove()
      }
      cleanup ::= stop
      stop
    }

    def takeIdle(): Vector[js.Function1[js.Any, Unit]] = {
      val callbacks = pending
      pending = Vector.empty
      callbacks
    }
    def fire(callback: js.Function1[js.Any, Unit]): Unit = callback(js.undefined)
    def input(element: Element, value: String): Unit = {
      val textarea = element.ref.querySelector("textarea").asInstanceOf[dom.html.TextArea]
      textarea.value = value
      textarea.dispatchEvent(new dom.Event("input"))
    }
    def nextTurn(): Future[Unit] = {
      val completed = Promise[Unit]()
      setTimeout(0) { completed.success(()); () }
      completed.future
    }
    def close(): Unit = try cleanup.foreach(_.apply()) finally {
      previous.foreach { (name, value, existed) =>
        if existed then globals.updateDynamic(name)(value)
        else js.Dynamic.global.Reflect.deleteProperty(globals, name)
      }
      window.close()
    }
  }

  private def withControlledMounts(body: ControlledMounts => Future[Unit]): Future[Unit] = {
    val fixture = new ControlledMounts
    Try(body(fixture)).fold(Future.failed, identity).transform { result =>
      fixture.close()
      result
    }
  }

  test("a surviving mount retains fallback edits and enhancement after a transient sibling unmounts") {
    withControlledMounts { fixture =>
      val original = "\r\nclass Drawing {\r\n\t"
      val content = Var(original)
      var inputs = Vector.empty[String]
      val editor = CodeMirrorEditor(content, value => inputs :+= value, language = Java)
      val first = editor.getDomElement()
      val transient = editor.getDomElement()
      val stopFirst = fixture.mount(first)
      val stopTransient = fixture.mount(transient)
      fixture.nextTurn().flatMap { _ =>
        val callbacks = fixture.takeIdle()
        assertEquals(callbacks.size, 2)
        stopTransient()
        assert(first.ref.isConnected)
        assert(!transient.ref.isConnected)
        val draft = original.replace("\r\n", "\n") + "int value = ;\n"
        fixture.input(first, draft)
        assertEquals(content.now(), draft)
        assertEquals(inputs, Vector(draft))
        fixture.fire(callbacks(1))
        assertEquals(fixture.created.size, 0)
        fixture.input(transient, "stale fallback")
        assertEquals(content.now(), draft)
        fixture.fire(callbacks(0))
        fixture.nextTurn().map { _ =>
          assertEquals(fixture.created.size, 1)
          val current = fixture.created.head
          assert(current.config.parent eq first.ref)
          assertEquals(current.doc, draft)
          assertEquals(editor.currentDoc, Some(draft))
          current.input(draft + " ")
          assertEquals(inputs, Vector(draft, draft + " "))
          content.set(original)
          assertEquals(current.doc, original)
          assertEquals(inputs.size, 2)
          stopFirst()
          assertEquals(current.destroys, 1)
          current.input("stale editor")
          assertEquals(content.now(), original)
          assertEquals(inputs.size, 2)
          assertEquals(editor.currentDoc, None)
        }
      }
    }
  }

  test("a cached Java full-source editor enhances again after a Python view and retains raw edits") {
    withControlledMounts { fixture =>
      val original = "public class Drawing {\r\n\tpublic static void main(String[] args) {\r\n\t\tint n = 1;\r\n\t}\r\n}\r\n"
      val state = Var[ProgrammingState](ProgrammingStateJavaString(original))
      var published = Vector.empty[ProgrammingState]
      val editor = new JavaFunctionBasedEditor(state, onStateEdited = value => published :+= value)
      val cached = editor.getDomElement()
      val active = Var[Option[Element]](Some(cached))
      val stop = fixture.mount(div(child.maybe <-- active.signal))
      cached.ref.querySelector("button[aria-label='Full source']").asInstanceOf[dom.html.Button].click()
      fixture.nextTurn().flatMap { _ =>
        fixture.takeIdle().foreach(fixture.fire)
        fixture.nextTurn()
      }.flatMap { _ =>
        assertEquals(fixture.created.count(_.destroys == 0), 1)
        val previous = fixture.created.find(_.destroys == 0).get
        assertEquals(previous.doc, original)
        active.set(Some(div("Python view")))
        assertEquals(previous.destroys, 1)
        active.set(Some(cached))
        fixture.nextTurn()
      }.flatMap { _ =>
        assertEquals(state.now().toJava.code, original)
        val draft = original.replace("\r\n", "\n") + "\t "
        fixture.input(cached, draft)
        assertEquals(state.now().toJava.code, draft)
        assertEquals(published.size, 1)
        val fingerprint = state.now().fingerprint()
        fixture.takeIdle().foreach(fixture.fire)
        fixture.nextTurn().map { _ =>
          val current = fixture.created.filter(_.destroys == 0)
          assertEquals(current.size, 1)
          assert(current.head.config.parent.isConnected)
          assertEquals(current.head.doc, draft)
          assertEquals(state.now().fingerprint(), fingerprint)
          stop()
          assert(fixture.created.forall(_.destroys == 1))
        }
      }
    }
  }

  test("a ready handle survives a pending sibling and enhanced siblings publish each input once") {
    withControlledMounts { fixture =>
      val content = Var("initial")
      var inputs = Vector.empty[String]
      val editor = CodeMirrorEditor(content, value => inputs :+= value)
      val first = editor.getDomElement()
      val stopFirst = fixture.mount(first)
      fixture.nextTurn().flatMap { _ =>
        fixture.takeIdle().foreach(fixture.fire)
        fixture.nextTurn()
      }.flatMap { _ =>
        assertEquals(fixture.created.size, 1)
        val transient = editor.getDomElement()
        val stopTransient = fixture.mount(transient)
        fixture.nextTurn().map { _ =>
          stopTransient()
          fixture.takeIdle().foreach(fixture.fire)
          editor.focus()
          editor.setDiagnostics(List(CodeMirrorEditor.Diagnostic(1, message = "retained")))
          val current = fixture.created.head
          assertEquals(current.focuses, 1)
          assertEquals(current.diagnosticCalls.last, Vector("retained"))
          assertEquals(current.destroys, 0)
          assertEquals(editor.currentDoc, Some("initial"))
          assertEquals(fixture.created.size, 1)
          fixture.mount(editor.getDomElement())
        }
      }.flatMap { stopSecond =>
        fixture.nextTurn().flatMap { _ =>
          fixture.takeIdle().foreach(fixture.fire)
          fixture.nextTurn()
        }.map { _ =>
          assertEquals(fixture.created.size, 2)
          val firstHandle = fixture.created.head
          val secondHandle = fixture.created.last
          firstHandle.input("from first")
          assertEquals(content.now(), "from first")
          assertEquals(secondHandle.doc, "from first")
          assertEquals(inputs, Vector("from first"))
          secondHandle.input("from second")
          assertEquals(firstHandle.doc, "from second")
          assertEquals(inputs, Vector("from first", "from second"))
          stopSecond()
          secondHandle.input("stale sibling")
          assertEquals(content.now(), "from second")
          assertEquals(inputs.size, 2)
          assertEquals(editor.currentDoc, Some("from second"))
          stopFirst()
          assert(fixture.created.forall(_.destroys == 1))
        }
      }
    }
  }

  test("pending diagnostics survive a transient sibling while the remaining mount is waiting") {
    withControlledMounts { fixture =>
      val editor = CodeMirrorEditor(Var("unchanged"))
      val stopFirst = fixture.mount(editor.getDomElement())
      val stopTransient = fixture.mount(editor.getDomElement())
      editor.setDiagnostics(List(CodeMirrorEditor.Diagnostic(1, message = "pending")))
      fixture.nextTurn().flatMap { _ =>
        val callbacks = fixture.takeIdle()
        assertEquals(callbacks.size, 2)
        stopTransient()
        callbacks.foreach(fixture.fire)
        fixture.nextTurn().map { _ =>
          assertEquals(fixture.created.size, 1)
          assertEquals(fixture.created.head.diagnosticCalls, Vector(Vector("pending")))
          stopFirst()
          assertEquals(fixture.created.head.destroys, 1)
        }
      }
    }
  }

  test("mount yields and sends the configured Python gutter to the facade, while unmount destroys handles") {
    withControlledMounts { fixture =>
      val stops = List((Python, false), (Python, true), (Java, true)).map { (language, enabled) =>
        fixture.mount(CodeMirrorEditor(Var("print(1)"), language = language,
          editorConfig = CodeMirrorEditorConfig(enabled)).getDomElement())
      }
      assertEquals(fixture.created.size, 0, "even a cached facade is not mounted synchronously")
      fixture.nextTurn().flatMap { _ =>
        val callbacks = fixture.takeIdle()
        assertEquals(callbacks.size, 3)
        callbacks.foreach(fixture.fire)
        fixture.nextTurn().map { _ =>
          assertEquals(fixture.created.map(current => !js.isUndefined(current.config.flowchart)), Vector(false, true, false))
          stops.foreach(_.apply())
          assert(fixture.created.forall(_.destroys == 1))
        }
      }
    }
  }
}
