package it.evadid.homepage.webElements.editor.code.program

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.distribution.commandTypes.WorkerCommands.{RunPythonRequest, RunPythonResult}
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.webElements.editor.code.worker.{LocalWorker, SharedWorker}
import it.evadid.vm.analysis.ProgramAnalysis
import it.evadid.vm.simulation.python.PythonTurtleCalls
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class ProgrammingExecutionPanelSpec extends EditorDomSuite {
  private val labels = ProgrammingExecutionLabels(Val("console"), Val("run"), Val("stop"), Val("running"), Val("stopped"), Val("unavailable"))

  /** The worker's analysis is the core one; Pyodide is replaced by `python`, which records each request. */
  private final class Fixture(python: RunPythonRequest => Future[RunPythonResult]) {
    val requests = ListBuffer.empty[RunPythonRequest]
    private val executor = ExecutionClient(Seq(
      WorkerCommands.programAnalysis.toLocalExecutionClient((state, _) => Future(ProgramAnalysis.of(state))),
      WorkerCommands.runPython.toLocalExecutionClient((request, _) => { requests += request; python(request) })))
    val analysisWorkers = LocalWorker.Factory(executor)
    val runWorkers = LocalWorker.Factory(executor)
    def panel(source: Signal[ProgrammingState]): Element =
      ProgrammingExecutionPanel(source, labels, ProgramAnalyzer(SharedWorker(() => analysisWorkers())), () => ProgramRunner(() => runWorkers())).getDomElement()
  }

  /** Runs the converted Python on the shared interpreter, standing in for Pyodide's console. */
  private def console(request: RunPythonRequest): Future[RunPythonResult] = Future {
    val execution = ProgrammingStateRaw(Python, request.code).toBeExpression.programExecution
    RunPythonResult(ujson.Null, execution.stdout, execution.stderr, Nil)
  }

  private def run(root: dom.Element) = root.querySelector(".programming-execution__run").asInstanceOf[dom.HTMLButtonElement]
  private def text(root: dom.Element, selector: String) = root.querySelector(selector).textContent

  Seq[ProgrammingState](
    ProgrammingStateJavaStructured("class Main { static void main(String[] args) { System.out.println(\"hello\"); System.out.println(); Turtle.forward(10); } }"),
    ProgrammingStateRaw(Python, "print(\"hello\")\nprint()\nforward(10)\n")
  ).foreach { program =>
    test(s"${program.associatedLanguage} runs as Python in the worker with exact console newlines; turtle calls stay silent") {
      val fixture = Fixture(console)
      val source = Var(program)
      val panel = fixture.panel(source.signal)
      val close = mount(panel)
      eventually(!run(panel.ref).disabled, "the worker analysed the program").flatMap { _ =>
        run(panel.ref).click()
        eventually(text(panel.ref, "[role=log]") == "hello\n\n", panel.ref.textContent)
      }.map { _ =>
        assertEquals(fixture.requests.map(_.turtleCalls).toList, List(PythonTurtleCalls.Ignored))
        program match { case ProgrammingStateRaw(Python, code) => assertEquals(fixture.requests.head.code, code, "Python runs as written"); case _ => () }
      }.andThen(_ => close())
    }
  }

  test("a program without a Python form cannot run and says why") {
    val fixture = Fixture(console)
    val panel = fixture.panel(Val(ProgrammingStateRaw(Java, "class {")))
    val close = mount(panel)
    val message = panel.ref.querySelector(".programming-execution__unavailable")
    eventually(!message.hasAttribute("hidden"), "the analysis explains the blocked run").map { _ =>
      assert(run(panel.ref).disabled)
      assert(message.textContent.startsWith("unavailable ("), message.textContent)
      assert(fixture.requests.isEmpty)
    }.andThen(_ => close())
  }

  test("stopping and editing terminate the running worker and ignore its late result") {
    val pending = Promise[RunPythonResult]()
    val fixture = Fixture(_ => pending.future)
    val source = Var[ProgrammingState](ProgrammingStateRaw(Python, "while True:\n    pass\n"))
    val panel = fixture.panel(source.signal)
    val close = mount(panel)
    def stop = panel.ref.querySelector(".programming-execution__stop").asInstanceOf[dom.HTMLButtonElement]
    eventually(!run(panel.ref).disabled, "analysed").flatMap { _ =>
      run(panel.ref).click()
      eventually(!stop.disabled && text(panel.ref, ".programming-execution__state") == "running", "running")
    }.flatMap { _ =>
      stop.click()
      assert(fixture.runWorkers.created.head.terminated, "the endless loop's worker is terminated")
      assertEquals(text(panel.ref, ".programming-execution__state"), "stopped")
      pending.success(RunPythonResult(ujson.Null, "late", "", Nil))
      source.set(ProgrammingStateRaw(Python, "print(1)\n"))
      eventually(!run(panel.ref).disabled, "the edited program is analysed")
    }.map { _ =>
      assertEquals(text(panel.ref, "[role=log]"), "", "a stopped run never reports output")
      assertEquals(text(panel.ref, ".programming-execution__state"), "")
    }.andThen(_ => close())
  }
}
