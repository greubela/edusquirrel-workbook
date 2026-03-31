package interactionPlugins.programmingExercise.pythonExercise.turtle

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import contentmanagement.webElements.genericHtmlElements.canvas.WebCanvas
import contentmanagement.webElements.genericHtmlElements.editor.CodeMirrorEditor
import interactionPlugins.programmingExercise.pythonExercise.data.PythonExecutionRequest
import interactionPlugins.programmingExercise.pythonExercise.pyodide.{PyodideEnvironment, PyodideWorkerEnvironment}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

case class TurtleExerciseDemo() extends HtmlAppElement {

  private val codeVar = Var(
    """from turtle import *
      |forward(120)
      |left(120)
      |forward(120)
      |left(120)
      |forward(120)
      |x: int = 3
      |print(x)
      |y: float = 5
      |z = x + y
      |print("res: " + str(z))
      |""".stripMargin
  )
  private val stdoutVar = Var("")
  private val stderrVar = Var("")
  private val globalsVar = Var("{}")

  private val outputCanvas: WebCanvas = WebCanvas(1000, 1000)
  private val pyodideEnvironment: PyodideEnvironment = {
    val environment = new PyodideWorkerEnvironment()
    environment.register(TurtleBackend(outputCanvas))
    environment
  }

  private val inputEditorElement = CodeMirrorEditor(codeVar)

  private val startButton: Element = button(
    "Run turtle",
    onClick --> (_ => runCurrentCode())
  )

  private def runCurrentCode(): Unit = {
    stdoutVar.set("")
    stderrVar.set("")
    globalsVar.set("{}")

    val request = PythonExecutionRequest(codeVar.now(), Some(20_000))
    pyodideEnvironment.executeCodeFull(request).foreach { result =>
      stdoutVar.set(result.state.stdout)
      stderrVar.set(result.state.stderr)
      globalsVar.set(js.JSON.stringify(result.state.globals.toJSDictionary.asInstanceOf[js.Any]))
    }
  }

  private val outputStdOut: Element = pre(child.text <-- stdoutVar.signal)
  private val outputStdErr: Element = pre(color := "#b00020", child.text <-- stderrVar.signal)
  private val globalVariables: Element = pre(child.text <-- globalsVar.signal)

  override def getDomElement(): L.Element = {
    div(
      cls := "turtle-exercise-demo",
      display.flex,
      gap := "1rem",
      div(flex := "1", inputEditorElement.getDomElement()),
      div(
        width := "420px",
        display.flex,
        flexDirection.column,
        gap := "0.75rem",
        startButton,
        outputCanvas.getDomElement(),
        h4("stdout"),
        outputStdOut,
        h4("stderr"),
        outputStdErr,
        h4("globals"),
        globalVariables
      )
    )
  }
}
