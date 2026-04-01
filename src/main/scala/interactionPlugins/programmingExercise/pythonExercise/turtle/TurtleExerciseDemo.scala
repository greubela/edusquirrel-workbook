package interactionPlugins.programmingExercise.pythonExercise.turtle

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import contentmanagement.webElements.genericHtmlElements.editor.CodeMirrorEditor
import interactionPlugins.programmingExercise.pythonExercise.data.PythonExecutionRequest
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.*
import workbook.htmlElements.basic.canvas.WebCanvas

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

  private val turtleExecutionHandler = ExecuteTurtleOps(outputCanvas)

  private val turtleBackend = PyodideWorkerClient()

  private val inputEditorElement = CodeMirrorEditor(codeVar)

  private val startButton: Element = button(
    "Run turtle",
    onClick --> (_ => runCurrentCode())
  )

  private def runCurrentCode(): Unit = {
    println("runCurrentCode!: " + codeVar.now().toString.split("\n").length + " lines")
    stdoutVar.set("")
    stderrVar.set("")
    globalsVar.set("{}")
    turtleExecutionHandler.reset()
    PyodideWorkerClient.executeTurtleCode[Double](turtleBackend, codeVar.now()).onComplete {
      case scala.util.Success(res: TurtleExecutionResult[Double]) =>
        println("res 1: " + res)
      case scala.util.Failure(exception) =>
        println("execution failure 1: " + exception.getMessage)
        stderrVar.set(exception.getMessage)
    }
    turtleBackend.runWithCallbackLibrary(codeVar.now(), turtleExecutionHandler.callbackLibrary).onComplete {
      case scala.util.Success(runReport: PythonRunReport) =>
        println("successfully executed, commands 2: " + runReport.callbackOps.mkString("\n", " -> ", "\n") + "report: " + runReport)
        stdoutVar.set(runReport.stdout)
        stderrVar.set(runReport.stderr)
      case scala.util.Failure(exception) =>
        println("execution failure 2: " + exception.getMessage)
        stderrVar.set(exception.getMessage)
    }/*
    pyodideEnvironment.executeCodeFull(request).onComplete {
      case scala.util.Success(result) =>
        println("successfully executed, result: " + result)
        stdoutVar.set(result.state.stdout)
        stderrVar.set(result.state.stderr)
        globalsVar.set(js.JSON.stringify(result.state.globals.toJSDictionary.asInstanceOf[js.Any]))
      case scala.util.Failure(exception) =>
        println("execution failure: " + exception.getMessage)
        stderrVar.set(exception.getMessage)
    }*/
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
        width := "750px",
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
