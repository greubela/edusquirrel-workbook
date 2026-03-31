package interactionPlugins.programmingExercise.pythonExercise.turtle

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import contentmanagement.webElements.genericHtmlElements.canvas.WebCanvas
import contentmanagement.webElements.genericHtmlElements.editor.CodeMirrorEditor
import interactionPlugins.programmingExercise.pythonExercise.data.PythonExecutionRequest
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.ExecutionBackend
import org.scalajs.dom

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

  val outputCanvas: WebCanvas = WebCanvas(1000, 1000)
  val turtleBackend: TurtleBackend = new TurtleBackendImpl(outputCanvas)
  // Turtle integration uses synchronous JS callbacks (for values such as default_turtle_id).
  // Those callbacks are not return-value-compatible with the worker bridge, so keep this
  // environment on the main thread.
  val pyodideEnvironment: PyodideEnvironment = new PyodideEnvironment(ExecutionBackend.MainThread)

  private val inputEditorElement = CodeMirrorEditor(codeVar)

  private val isReady = Var(false)

  private def asDouble(v: js.Any): Double =
    js.typeOf(v) match {
      case "number" => v.asInstanceOf[Double]
      case "string" => v.toString.toDouble
      case "boolean" => if v.asInstanceOf[Boolean] then 1.0 else 0.0
      case _ => v.toString.toDouble
    }

  private def asInt(v: js.Any): Int = asDouble(v).toInt
  private def asBoolean(v: js.Any): Boolean = v.asInstanceOf[Boolean]
  private def asString(v: js.Any): String = v.toString

  private val backendCallbacks: Map[String, Seq[js.Any] => js.Any] = Map(
    "create_turtle" -> (_ => turtleBackend.createTurtle()),
    "default_turtle_id" -> (_ => turtleBackend.defaultTurtleId),
    "prepare_for_run" -> (_ => turtleBackend.prepareForRun()),
    "call_turtle" -> (args => {
      val id = asInt(args(0))
      val method = asString(args(1))
      val mArgs = args.drop(2)
      method match {
        case "forward" | "fd" => turtleBackend.turtleForward(id, asDouble(mArgs(0)))
        case "backward" | "back" | "bk" => turtleBackend.turtleBackward(id, asDouble(mArgs(0)))
        case "left" | "lt" => turtleBackend.turtleLeft(id, asDouble(mArgs(0)))
        case "right" | "rt" => turtleBackend.turtleRight(id, asDouble(mArgs(0)))
        case "goto" | "setpos" | "setposition" => turtleBackend.turtleGoTo(id, asDouble(mArgs(0)), asDouble(mArgs(1)))
        case "setx" => turtleBackend.turtleSetX(id, asDouble(mArgs(0)))
        case "sety" => turtleBackend.turtleSetY(id, asDouble(mArgs(0)))
        case "setheading" | "seth" => turtleBackend.turtleSetHeading(id, asDouble(mArgs(0)))
        case "home" => turtleBackend.turtleHome(id)
        case "penup" | "pu" | "up" => turtleBackend.turtlePenUp(id)
        case "pendown" | "pd" | "down" => turtleBackend.turtlePenDown(id)
        case "pensize" | "width" =>
          if mArgs.nonEmpty then { turtleBackend.turtlePenSizeSet(id, asDouble(mArgs(0))); () }
          else turtleBackend.turtlePenSizeGet(id)
        case "pencolor" =>
          if mArgs.nonEmpty then { turtleBackend.turtlePenColorSet(id, asString(mArgs(0))); () }
          else turtleBackend.turtlePenColorGet(id)
        case "fillcolor" =>
          if mArgs.nonEmpty then { turtleBackend.turtleFillColorSet(id, asString(mArgs(0))); () }
          else turtleBackend.turtleFillColorGet(id)
        case "color" =>
          if mArgs.nonEmpty then {
            val pen = asString(mArgs(0))
            val fill = if mArgs.length >= 2 then asString(mArgs(1)) else pen
            turtleBackend.turtleColorSet(id, pen, fill)
            ()
          } else turtleBackend.turtleColorGet(id)
        case "position" | "pos" => turtleBackend.turtlePosition(id)
        case "xcor" => turtleBackend.turtleXCor(id)
        case "ycor" => turtleBackend.turtleYCor(id)
        case "heading" => turtleBackend.turtleHeading(id)
        case "distance" => turtleBackend.turtleDistance(id, asDouble(mArgs(0)), asDouble(mArgs(1)))
        case "isdown" => turtleBackend.turtleIsDown(id)
        case "showturtle" | "st" => turtleBackend.turtleShowTurtle(id)
        case "hideturtle" | "ht" => turtleBackend.turtleHideTurtle(id)
        case "isvisible" => turtleBackend.turtleIsVisible(id)
        case "clear" => turtleBackend.turtleClear(id)
        case "reset" => turtleBackend.turtleReset(id)
        case _ =>
          dom.console.warn(s"Unsupported turtle method: $method")
          ()
      }
    }),
    "call_screen" -> (args => {
      val method = asString(args(0))
      val mArgs = args.drop(1)
      method match {
        case "bgcolor" =>
          if mArgs.nonEmpty then { turtleBackend.screenBgColorSet(asString(mArgs(0))); () }
          else turtleBackend.screenBgColorGet()
        case "clear" | "clearscreen" => turtleBackend.screenClearScreen()
        case "reset" | "resetscreen" => turtleBackend.screenResetScreen()
        case "title" => if mArgs.nonEmpty then turtleBackend.screenTitleSet(asString(mArgs(0))) else turtleBackend.screenTitleGet()
        case _ =>
          dom.console.warn(s"Unsupported screen method: $method")
          ()
      }
    })
  )

  private val setupFuture = for {
    _ <- pyodideEnvironment.registerSyncModule("_scalajs_turtle_backend", backendCallbacks)
    _ <- pyodideEnvironment.executeCodeFull(PythonExecutionRequest(TurtlePythonModule.moduleBootstrapPython, None))
  } yield isReady.set(true)

  setupFuture.failed.foreach { error =>
    stderrVar.set(s"Failed to initialize turtle runtime: ${error.getMessage}")
    dom.console.error("Failed to initialize turtle runtime", error)
  }

  private val startButton: Element = button(
    "Run turtle",
    onClick --> (_ => runCurrentCode())
  )

  private def runCurrentCode(): Unit = {
    if !isReady.now() then return
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

  val outputStdOut: Element = pre(child.text <-- stdoutVar.signal)
  val outputStdErr: Element = pre(color := "#b00020", child.text <-- stderrVar.signal)
  val globalVariables: Element = pre(child.text <-- globalsVar.signal)

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
