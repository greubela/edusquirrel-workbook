package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.{TurtleDrawingComparison, TurtlePathBuilder}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.elements.interactionElements.programming.{JavaTurtleCase, JavaTurtleTask, ProgrammingState, TurtleGraphic}

import java.util.concurrent.CancellationException
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}

object JavaTurtleExecutionPanel {
  enum Status {
    case Idle, Running, Stopped
    case Ready(commands: List[TurtleCommand[Double]], matches: Option[Boolean])
    case Assessed(drawings: Vector[List[TurtleCommand[Double]]], matches: Vector[Boolean])
    case Failed(message: String)
  }

  private val tolerance = 0.25
  private val maxSamples = 20000L
  private val maxComparisons = 4000000L
  private val lineCommands = Set("forward", "fd", "backward", "back", "bk", "right", "rt", "turn", "turnright", "turn_right",
    "left", "lt", "turnleft", "turn_left", "penup", "pen_up", "pu", "up", "pendown", "pen_down", "pd", "down",
    "goto", "gotoxy", "goto_x_y", "setpos", "setposition", "setx", "set_x", "setxposition", "sety", "set_y", "setyposition",
    "setheading", "set_heading", "seth", "home", "clear", "clearscreen", "reset")

  private def path(commands: List[TurtleCommand[Double]]): TurtlePathBuilder[Double] =
    TurtlePathBuilder[Double](Point(0.0, 0.0), commands, 0.0)

  private[code] def validateDrawing(commands: List[TurtleCommand[Double]]): Unit = {
    val drawing = path(commands)
    val points = TurtleDrawingComparison.extractSegments(drawing).flatMap(line => List(line.from, line.to)) ++
      List(Point(drawing.turtleState.x, drawing.turtleState.y), Point(0.0, 0.0))
    val minX = points.map(_.x).min
    val minY = points.map(_.y).min
    val width = (points.map(_.x).max - minX).max(1.0)
    val height = (points.map(_.y).max - minY).max(1.0)
    val padding = width.max(height) * 0.08
    if points.exists(point => !point.x.isFinite || !point.y.isFinite) ||
      List(minX - padding, minY - padding, width + padding * 2, height + padding * 2).exists(!_.isFinite) then
      throw IllegalArgumentException("Your drawing exceeds the display range. Check its distances and angles.")
  }

  private[code] def compare(commands: List[TurtleCommand[Double]], target: TurtleGraphic): Boolean = {
    val expected = target.toTurtleProgram.toList
    if (commands ++ expected).exists(command =>
      !lineCommands.contains(command.name.trim.toLowerCase.replace('-', '_')) || command.args.exists(!_.isFinite)) then
      throw IllegalArgumentException("This drawing uses commands that cannot be compared yet.")
    val actualPath = path(commands)
    val targetPath = path(expected)
    val actualLines = TurtleDrawingComparison.extractSegments(actualPath)
    val targetLines = TurtleDrawingComparison.extractSegments(targetPath)
    def samples(lines: List[TurtleDrawingComparison.Segment]): Long =
      lines.foldLeft(0L) { (total, line) =>
        val count = math.ceil(line.length / (tolerance / 2.0)) + 1.0
        if !count.isFinite || count > maxSamples then maxSamples + 1
        else (total + count.toLong).min(maxSamples + 1)
      }
    val actualSamples = samples(actualLines)
    val targetSamples = samples(targetLines)
    if actualSamples + targetSamples > maxSamples ||
      actualSamples * targetLines.size + targetSamples * actualLines.size > maxComparisons then
      throw IllegalArgumentException("Your drawing is too large to compare. Check the distances in your program.")
    TurtleDrawingComparison.compare(targetPath, actualPath, Some(tolerance)).matches
  }
}

final class JavaTurtleExecutionPanel(
    source: Var[ProgrammingState],
    execute: () => Future[List[TurtleCommand[Double]]],
    cancel: () => Unit,
    target: Option[TurtleGraphic] = None,
    assessment: Option[(JavaTurtleTask, () => Future[Vector[List[TurtleCommand[Double]]]])] = None
) extends HtmlAppElement {
  import JavaTurtleExecutionPanel.*

  private[code] val status = Var[Status](Status.Idle)
  private var revision = 0L
  private var owner = Option.empty[ManualOwner]

  private[code] def activate(): Unit = if owner.isEmpty then {
    val mountedOwner = new ManualOwner
    owner = Some(mountedOwner)
    var previous = source.now()
    source.signal.changes.foreach { next =>
      if next != previous then { previous = next; reset() }
    }(using mountedOwner)
  }

  private[code] def deactivate(): Unit = {
    val previous = owner
    owner = None
    previous.foreach(_.killSubscriptions())
    reset()
  }

  private[code] def reset(): Unit = {
    revision += 1
    val running = status.now() == Status.Running
    status.set(Status.Idle)
    if running then cancel()
  }

  private[code] def run(): Unit = start(execute) { commands =>
    validateDrawing(commands)
    Status.Ready(commands, target.map(compare(commands, _)))
  }

  private[code] def checkTask(): Unit = assessment.foreach { (task, executeCases) =>
    start(executeCases) { drawings =>
      if drawings.size != task.cases.size || drawings.isEmpty then
        throw IllegalStateException("The task could not be checked completely.")
      Status.Assessed(drawings, drawings.zip(task.cases).map { (commands, example) => compare(commands, example.expectedShape) })
    }
  }

  private def start[A](execute: () => Future[A])(completed: A => Status): Unit = {
    if status.now() == Status.Running then return
    revision += 1
    val requested = revision
    val original = source.now()
    status.set(Status.Running)
    if requested != revision || status.now() != Status.Running then return
    Try(execute()).fold(Future.failed, identity).onComplete {
      case _ if requested != revision || original != source.now() => ()
      case Success(result) =>
        Try(completed(result)) match {
          case Success(next) => status.set(next)
          case Failure(error) => status.set(Status.Failed(Option(error.getMessage).getOrElse("The drawing could not be compared.")))
        }
      case Failure(_: CancellationException) => status.set(Status.Stopped)
      case Failure(error) => status.set(Status.Failed(Option(error.getMessage).getOrElse("Your program could not finish.")))
    }
  }

  private[code] def stop(): Unit = if status.now() == Status.Running then {
    revision += 1
    status.set(Status.Stopped)
    cancel()
  }

  private def picture(commands: List[TurtleCommand[Double]], actual: List[TurtleCommand[Double]]): Element = {
    val drawing = TurtlePathBuilder[Double](Point(0.0, 0.0), commands, 0.0)
    val reference = target.toList.flatMap(_.toTurtleProgram)
    val paths = List(reference.toList, actual).map(commands => TurtlePathBuilder[Double](Point(0.0, 0.0), commands, 0.0))
    val ends = paths.flatMap(TurtleDrawingComparison.extractSegments).flatMap(line => List(line.from, line.to)) :+ Point(0.0, 0.0)
    val minX = ends.map(_.x).min
    val minY = ends.map(_.y).min
    val width = (ends.map(_.x).max - minX).max(1.0)
    val height = (ends.map(_.y).max - minY).max(1.0)
    val padding = width.max(height) * 0.08
    div(svg.svg(svg.viewBox := s"${minX-padding} ${minY-padding} ${width+padding*2} ${height+padding*2}",
      svg.width := "100%", svg.height := "180", aria.label := "Turtle drawing",
      svg.path(svg.d := drawing.svgPathBuilder.toSvgPathD, svg.fill := "none", svg.stroke := "#334155",
        svg.strokeWidth := (width.max(height) / 180.0).toString)))
  }

  private lazy val domElement = div(
    cls := "java-turtle-execution",
    onMountCallback(_ => activate()),
    onUnmountCallback(_ => deactivate()),
    h3("Drawing"),
    assessment.fold[Modifier[HtmlElement]](emptyMod) { (task, _) =>
      def call(example: JavaTurtleCase): String = example.call(task.methodName)
      val emptyTargets = task.cases.filter(_.expectedShape.toTurtleProgram.isEmpty).map(call)
      p(s"Use the parameters of ${task.methodName} to draw the requested shape. " +
        s"Check task calls ${task.cases.map(call).mkString(", ")}. " +
        (if emptyTargets.nonEmpty then s"${emptyTargets.mkString(", ")} should draw no lines." else ""))
    },
    div(cls := "java-turtle-execution__actions",
      button(typ := "button", cls := "java-function-editor__button java-function-editor__button--primary",
        disabled <-- status.signal.map(_ == Status.Running), "Run", onClick --> (_ => run())),
      button(typ := "button", cls := "java-function-editor__button java-function-editor__button--secondary",
        disabled <-- status.signal.map(_ != Status.Running), "Stop", onClick --> (_ => stop())),
      assessment.fold[Modifier[HtmlElement]](emptyMod)(_ => button(typ := "button",
        cls := "java-function-editor__button java-function-editor__button--primary",
        disabled <-- status.signal.map(_ == Status.Running), "Check task", onClick --> (_ => checkTask())))
    ),
    p(cls := "java-turtle-execution__status", role := "status", aria.live := "polite",
      child.text <-- status.signal.map {
        case Status.Idle => "Run your program to see its drawing."
        case Status.Running => "Your program is running…"
        case Status.Stopped => "Stopped. You can edit your program and run it again."
        case Status.Ready(_, Some(true)) => "Your drawing matches the target. Nicely done!"
        case Status.Ready(_, Some(false)) => "Your drawing doesn't match the target yet. Check the distances and turns."
        case Status.Ready(_, None) => "Here's your drawing."
        case Status.Assessed(_, matches) if matches.forall(identity) => "Your function draws the requested shapes. Nicely done!"
        case Status.Assessed(_, _) => "Your function doesn't draw every requested shape yet. Check how you use its parameters."
        case Status.Failed(_) => ""
      }),
    child.maybe <-- status.signal.map {
      case Status.Failed(message) => Some(p(cls := "java-turtle-execution__error", role := "alert", message))
      case _ => None
    },
    child.maybe <-- status.signal.map {
      case Status.Assessed(_, matches) => assessment.map { (task, _) =>
        ul(cls := "java-turtle-execution__cases", task.cases.zip(matches).map { (example, matched) =>
          li(s"${example.call(task.methodName)}: ${if matched then "Matches" else "Not yet"}")
        })
      }
      case _ => None
    },
    target.fold[Modifier[HtmlElement]](emptyMod)(graphic => figure(
      cls := "java-turtle-execution__figure", figCaption("Target"),
      child <-- status.signal.map { state =>
        val actual = state match
          case Status.Ready(commands, _) => commands
          case Status.Assessed(drawings, _) => drawings.head
          case _ => Nil
        picture(graphic.toTurtleProgram.toList, actual)
      })),
    child.maybe <-- status.signal.map {
      case Status.Ready(commands, _) => Some(figure(cls := "java-turtle-execution__figure", figCaption("Your drawing"), picture(commands, commands)))
      case Status.Assessed(drawings, _) => Some(figure(cls := "java-turtle-execution__figure", figCaption("Your first drawing"), picture(drawings.head, drawings.head)))
      case _ => None
    }
  )

  override def getDomElement(): Element = domElement
}
