package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python}
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.distribution.formats.{ExecutionClientResponse, WorkerExecutionProtocol}
import it.evadid.distribution.formats.ExecutionClientResponse.ExecutionClientResponseTimestamps
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateJavaString
import munit.FunSuite
import org.scalajs.dom
import java.time.LocalDateTime
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.setTimeout
import scala.util.Try
import upickle.default.{read, write}

class CodeMirrorEditorMountSpec extends FunSuite {
  private val logger = Logger.withNameAndPrefixes(Some("test"), PrintToStdLogger.printNothing)
  private def editorFor(content: Var[String], onUserInput: String => Unit = _ => (),
      language: ProgrammingLanguage = Python, editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default): CodeMirrorEditor =
    CodeMirrorEditor(logger, content, onUserInput, language, Var(editorConfig))

  private final class ControlledMounts {
    private val globals = js.Dynamic.global.globalThis
    private val requireFn = js.Dynamic.global.selectDynamic("require")
    private val projectRoot = js.Dynamic.global.process.cwd().asInstanceOf[String]
    private val window = js.Dynamic.newInstance(requireFn(projectRoot + "/node_modules/jsdom").JSDOM)(
      "<html><body></body></html>", js.Dynamic.literal(url = "https://example.test/homepage/workbookDesign/")).window
    private val names = List("window", "document", "Element", "Node", "Event", "HTMLElement",
      "HTMLInputElement", "HTMLTextAreaElement", "HTMLSelectElement", "Worker")
    private val previous = names.map(name => (name, globals.selectDynamic(name),
      js.Dynamic.global.Object.prototype.hasOwnProperty.call(globals, name).asInstanceOf[Boolean]))
    names.foreach(name => globals.updateDynamic(name)(if (name == "window") window else window.selectDynamic(name)))
    private val cleanup = Var(List.empty[() => Unit])
    private val pending = Var(Vector.empty[js.Function1[js.Any, Unit]])
    private val createdState = Var(Vector.empty[Created])
    def created: Vector[Created] = createdState.now()
    private val workersState = Var(Vector.empty[FakeWorker])
    def workers: Vector[FakeWorker] = workersState.now()
    val beforeCreate = Var[CodeMirrorEditor.EditorConfig => Unit](_ => ())

    private val createWorker: js.Function1[String, js.Object] = url => {
      val worker = new FakeWorker(url)
      workersState.update(_ :+ worker)
      worker.raw.asInstanceOf[js.Object]
    }
    globals.updateDynamic("Worker")(js.Dynamic.global.Function("create", "return function Worker(url) { return create(url); };")(createWorker))

    window.updateDynamic("requestIdleCallback")(((callback: js.Function1[js.Any, Unit], _: js.Any) => {
      pending.update(_ :+ callback)
      pending.now().size
    }): js.Function2[js.Function1[js.Any, Unit], js.Any, Int])
    window.updateDynamic("EduSquirrelCodeMirror")(js.Dynamic.literal(
      createEditor = ((config: CodeMirrorEditor.EditorConfig) => {
        beforeCreate.now()(config)
        val current = new Created(config)
        createdState.update(_ :+ current)
        current.handle
      }): js.Function1[CodeMirrorEditor.EditorConfig, CodeMirrorEditor.CodeMirrorHandle]
    ))

    final class FakeWorker(val url: String) {
      private val sentState = Var(Vector.empty[String])
      def sent: Vector[String] = sentState.now()
      private val terminationCount = Var(0)
      def terminations: Int = terminationCount.now()
      val raw: js.Dynamic = js.Dynamic.literal(
        postMessage = ((message: String) => { sentState.update(_ :+ message) }): js.Function1[String, Unit],
        terminate = (() => { terminationCount.update(_ + 1) }): js.Function0[Unit])

      def reply(label: String, index: Int = 0): Unit = {
        val request = read[WorkerExecutionProtocol.Request](sent(index))
        val chart = PythonFlowchartGutter.Chart(List(PythonFlowchartGutter.Node(1, "process", 0, label)), Nil, List(1))
        val now = LocalDateTime.now()
        val response = ExecutionClientResponse.forValue(ExecutionClientResponseTimestamps(now, now, now), chart, request.command)
        raw.selectDynamic("onmessage").asInstanceOf[js.Function1[dom.MessageEvent, Unit]](
          js.Dynamic.literal(data = write(WorkerExecutionProtocol.Response(request.requestId, response))).asInstanceOf[dom.MessageEvent])
      }
    }

    final class Created(val config: CodeMirrorEditor.EditorConfig) {
      private val document = Var(config.doc.getOrElse(""))
      def doc: String = document.now()
      private val destroyCount = Var(0)
      def destroys: Int = destroyCount.now()
      private val focusCount = Var(0)
      def focuses: Int = focusCount.now()
      private val diagnostics = Var(Vector.empty[Vector[String]])
      def diagnosticCalls: Vector[Vector[String]] = diagnostics.now()
      private val node = dom.document.createElement("div")
      node.setAttribute("class", "cm-content")
      node.textContent = doc
      config.parent.appendChild(node)
      val handle = js.Dynamic.literal(
        getDoc = (() => doc): js.Function0[String],
        setDoc = ((value: String) => { document.set(value); node.textContent = value; config.onDocChange(value) }): js.Function1[String, Unit],
        setDiagnostics = ((values: js.Array[js.Object]) => {
          diagnostics.update(_ :+ Vector.tabulate(values.length)(index => values(index).asInstanceOf[js.Dynamic].message.asInstanceOf[String]))
        }): js.Function1[js.Array[js.Object], Unit],
        focus = (() => { focusCount.update(_ + 1) }): js.Function0[Unit],
        destroy = (() => { destroyCount.update(_ + 1); node.remove() }): js.Function0[Unit]
      ).asInstanceOf[CodeMirrorEditor.CodeMirrorHandle]
      def input(value: String): Unit = { document.set(value); node.textContent = value; config.onDocChange(value) }
      def analyze(source: String): Future[js.Object] = config.flowchart.get.apply(source).toFuture
    }

    def mount(element: Element): () => Unit = {
      val host = dom.document.createElement("div")
      dom.document.body.appendChild(host)
      val mounted = render(host, element)
      val attached = Var(true)
      val stop = () => if attached.now() then {
        attached.set(false)
        mounted.unmount()
        host.remove()
      }
      cleanup.update(stop :: _)
      stop
    }

    def takeIdle(): Vector[js.Function1[js.Any, Unit]] = {
      val callbacks = pending.now()
      pending.set(Vector.empty)
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
    def close(): Unit = try cleanup.now().foreach(_.apply()) finally {
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
      val inputs = Var(Vector.empty[String])
      val editor = editorFor(content, value => inputs.update(_ :+ value), language = Java)
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
        assertEquals(inputs.now(), Vector(draft))
        fixture.fire(callbacks(1))
        assertEquals(fixture.created.size, 0)
        fixture.input(transient, "stale fallback")
        assertEquals(content.now(), draft)
        fixture.fire(callbacks(0))
        fixture.nextTurn().map { _ =>
          assertEquals(fixture.created.size, 1)
          val current = fixture.created.head
          assert(current.config.parent eq first.ref.querySelector(".code-mirror-editor"))
          assertEquals(current.doc, draft)
          assertEquals(editor.currentDoc, Some(draft))
          current.input(draft + " ")
          assertEquals(inputs.now(), Vector(draft, draft + " "))
          content.set(original)
          assertEquals(current.doc, original)
          assertEquals(inputs.now().size, 2)
          stopFirst()
          assertEquals(current.destroys, 1)
          current.input("stale editor")
          assertEquals(content.now(), original)
          assertEquals(inputs.now().size, 2)
          assertEquals(editor.currentDoc, None)
        }
      }
    }
  }

  test("a cached Java full-source editor enhances again after a Python view and retains raw edits") {
    withControlledMounts { fixture =>
      val original = "public class Drawing {\r\n\tpublic static void main(String[] args) {\r\n\t\tint n = 1;\r\n\t}\r\n}\r\n"
      val state = Var[ProgrammingState](ProgrammingStateJavaString(original))
      val published = Var(Vector.empty[ProgrammingState])
      val editor = new JavaFunctionBasedEditor(logger, state, onStateEdited = value => published.update(_ :+ value))
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
        assertEquals(published.now().size, 1)
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
      val inputs = Var(Vector.empty[String])
      val editor = editorFor(content, value => inputs.update(_ :+ value))
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
          assert(first.ref.querySelector("textarea").hasAttribute("hidden"))
          assert(!transient.ref.querySelector("textarea").hasAttribute("hidden"))
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
          editor.setDiagnostics(List(CodeMirrorEditor.Diagnostic(1, message = "both views")))
          assertEquals(firstHandle.diagnosticCalls.last, Vector("both views"))
          assertEquals(secondHandle.diagnosticCalls.last, Vector("both views"))
          firstHandle.input("from first")
          assertEquals(content.now(), "from first")
          assertEquals(secondHandle.doc, "from first")
          assertEquals(inputs.now(), Vector("from first"))
          secondHandle.input("from second")
          assertEquals(firstHandle.doc, "from second")
          assertEquals(inputs.now(), Vector("from first", "from second"))
          stopSecond()
          secondHandle.input("stale sibling")
          assertEquals(content.now(), "from second")
          assertEquals(inputs.now().size, 2)
          assertEquals(editor.currentDoc, Some("from second"))
          editor.focus()
          editor.clearDiagnostics()
          assertEquals(firstHandle.focuses, 2)
          assertEquals(firstHandle.diagnosticCalls.last, Vector.empty[String])
          assertEquals(secondHandle.diagnosticCalls.last, Vector("both views"))
          stopFirst()
          assert(fixture.created.forall(_.destroys == 1))
        }
      }
    }
  }

  test("pending diagnostics survive a transient sibling while the remaining mount is waiting") {
    withControlledMounts { fixture =>
      val editor = editorFor(Var("unchanged"))
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

  test("flowchart mounts own their workers and a remount rejects replies from closed views") {
    withControlledMounts { fixture =>
      val content = Var("print(1)")
      val editor = editorFor(content, language = Python, editorConfig = CodeMirrorEditorConfig.Flowchart)
      val first = editor.getDomElement()
      val stopFirst = fixture.mount(first)
      val stopSecond = fixture.mount(editor.getDomElement())
      def label(chart: js.Object): String = chart.asInstanceOf[js.Dynamic].nodes
        .asInstanceOf[js.Array[js.Dynamic]].head.label.asInstanceOf[String]

      for {
        _ <- fixture.nextTurn()
        _ = fixture.takeIdle().foreach(fixture.fire)
        _ <- fixture.nextTurn()
        firstView = fixture.created.head
        secondView = fixture.created.last
        firstResult = firstView.analyze("print(1)")
        removedResult = secondView.analyze("print(2)").failed
        _ = assertEquals(fixture.workers.size, 2)
        _ = assert(fixture.workers.forall(_.url == "https://example.test/artifacts/newest/worker.js"))
        _ = stopSecond()
        _ = assertEquals(fixture.workers.map(_.terminations), Vector(0, 1))
        _ = fixture.workers(1).reply("removed sibling")
        _ = fixture.workers.head.reply("survivor")
        firstChart <- firstResult
        removedError <- removedResult
        _ = assertEquals(label(firstChart), "survivor")
        _ = assert(removedError.getMessage.contains("closed"))
        pendingResult = firstView.analyze("print(3)").failed
        _ = assertEquals(fixture.workers.head.sent.size, 2)
        _ = assertEquals(firstView.destroys, 0)
        _ = stopFirst()
        _ = assertEquals(fixture.workers.map(_.terminations), Vector(1, 1))
        stopRemount = fixture.mount(first)
        _ <- fixture.nextTurn()
        _ = fixture.takeIdle().foreach(fixture.fire)
        _ <- fixture.nextTurn()
        remountedView = fixture.created.last
        cachedChart <- remountedView.analyze("print(1)")
        _ = assertEquals(label(cachedChart), "survivor")
        _ = assertEquals(fixture.workers.size, 2)
        currentResult = remountedView.analyze("print(4)")
        _ = assertEquals(fixture.workers.size, 3)
        _ = fixture.workers.last.reply("remounted")
        currentChart <- currentResult
        _ = fixture.workers.head.reply("late old mount", index = 1)
        _ <- fixture.nextTurn()
        repeatedChart <- remountedView.analyze("print(4)")
        pendingError <- pendingResult
        closedError <- firstView.analyze("print(4)").failed
        _ = assertEquals(label(currentChart), "remounted")
        _ = assertEquals(label(repeatedChart), "remounted")
        _ = assert(pendingError.getMessage.contains("closed"))
        _ = assert(closedError.getMessage.contains("closed"))
        _ = assertEquals(fixture.workers.size, 3)
        _ = assertEquals(fixture.workers.last.sent.size, 1)
        _ = assertEquals(content.now(), "print(1)")
        _ = assertEquals(editor.currentDoc, Some("print(1)"))
        _ = stopRemount()
      } yield {
        assert(fixture.created.forall(_.destroys == 1))
        assertEquals(fixture.workers.map(_.terminations), Vector(1, 1, 1))
      }
    }
  }

  test("a failed Python mount closes its worker and preserves the surviving view and fallback") {
    withControlledMounts { fixture =>
      val content = Var("print(1)")
      val inputs = Var(Vector.empty[String])
      val editor = editorFor(content, value => inputs.update(_ :+ value), language = Python,
        editorConfig = CodeMirrorEditorConfig.Flowchart)
      fixture.mount(editor.getDomElement())
      for {
        _ <- fixture.nextTurn()
        _ = fixture.takeIdle().foreach(fixture.fire)
        _ <- fixture.nextTurn()
        firstView = fixture.created.head
        firstResult = firstView.analyze("print(1)")
        failedAnalysis = Promise[Throwable]()
        _ = fixture.beforeCreate.set(config => {
          failedAnalysis.completeWith(config.flowchart.get.apply("print(2)").toFuture.failed)
          throw RuntimeException("Failed facade creation")
        })
        failedView = editor.getDomElement()
        _ = fixture.mount(failedView)
        _ <- fixture.nextTurn()
        _ = fixture.takeIdle().foreach(fixture.fire)
        failure <- failedAnalysis.future
        _ = assert(failure.getMessage.contains("closed"))
        _ = assertEquals(fixture.created.size, 1)
        _ = assertEquals(fixture.workers.map(_.terminations), Vector(0, 1))
        _ = assertEquals(firstView.destroys, 0)
        _ = assert(!failedView.ref.querySelector("textarea").hasAttribute("hidden"))
        _ = fixture.workers.head.reply("still active")
        _ <- firstResult
        _ = fixture.input(failedView, "print(3)")
        _ = assertEquals(content.now(), "print(3)")
        _ = assertEquals(inputs.now(), Vector("print(3)"))
        _ = assertEquals(firstView.doc, "print(3)")
        survivingResult = firstView.analyze("print(3)")
        _ = assertEquals(fixture.workers.size, 2)
        _ = assertEquals(fixture.workers.head.sent.size, 2)
        _ = fixture.workers.head.reply("surviving edit", index = 1)
        _ <- survivingResult
      } yield {
        assertEquals(editor.currentDoc, Some("print(3)"))
        assertEquals(fixture.workers.map(_.terminations), Vector(0, 1))
        assertEquals(firstView.destroys, 0)
      }
    }
  }

  test("mount yields and sends the configured Python gutter to the facade, while unmount destroys handles") {
    withControlledMounts { fixture =>
      val stops = List((Python, false), (Python, true), (Java, true)).map { (language, enabled) =>
        fixture.mount(editorFor(Var("print(1)"), language = language,
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

  test("changing the gutter replaces only its mount and keeps the replacement reachable") {
    withControlledMounts { fixture =>
      val config = Var(CodeMirrorEditorConfig.Default)
      val editor = CodeMirrorEditor(logger, Var("print(1)"), language = Python, config = config)
      val stop = fixture.mount(editor.getDomElement())
      for {
        _ <- fixture.nextTurn()
        _ = fixture.takeIdle().foreach(fixture.fire)
        _ <- fixture.nextTurn()
        first = fixture.created.head
        _ = config.set(CodeMirrorEditorConfig.Flowchart)
        _ = assertEquals(first.destroys, 1)
        _ <- fixture.nextTurn()
        _ = fixture.takeIdle().foreach(fixture.fire)
        _ <- fixture.nextTurn()
        current = fixture.created.last
        _ = editor.focus()
        _ = editor.setDiagnostics(List(CodeMirrorEditor.Diagnostic(1, message = "replacement")))
      } yield {
        assertEquals(fixture.created.size, 2)
        assert(!js.isUndefined(current.config.flowchart))
        assertEquals(current.focuses, 1)
        assertEquals(current.diagnosticCalls.last, Vector("replacement"))
        assertEquals(editor.currentDoc, Some("print(1)"))
        stop()
        assert(fixture.created.forall(_.destroys == 1))
      }
    }
  }
}
