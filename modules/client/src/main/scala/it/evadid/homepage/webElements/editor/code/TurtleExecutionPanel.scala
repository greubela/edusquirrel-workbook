package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.{TurtleDrawingGrading, TurtleJsxGraphRenderer}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}

import java.util.concurrent.CancellationException
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}

object TurtleExecutionPanel {
  enum Status {
    case Idle, Running, Stopped
    case Ready(commands: List[TurtleCommand[Double]], matches: Option[Boolean])
    case Failed(message: String)
  }

  private[code] def validateDrawing(commands: List[TurtleCommand[Double]]): Unit = TurtleDrawingGrading.validateDrawing(commands)
  private[code] def compare(commands: List[TurtleCommand[Double]], target: TurtleGraphic): Boolean =
    TurtleDrawingGrading.compare(commands, target).matches
}

final class TurtleExecutionPanel(
    source: Var[ProgrammingState],
    execute: () => Future[List[TurtleCommand[Double]]],
    cancel: () => Unit,
    target: Option[TurtleGraphic] = None,
    comparisonPolicy: TurtleDrawingPolicy = TurtleDrawingPolicy.Coverage,
    prepare: () => Unit = () => ()
) extends HtmlAppElement {
  import TurtleExecutionPanel.*

  private[code] val status = Var[Status](Status.Idle)
  private var revision = 0L
  private var owner = Option.empty[ManualOwner]
  private var scenes = Vector.empty[TurtleJsxGraphRenderer.Scene]

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
    scenes = Vector.empty
    status.set(Status.Idle)
    if running then cancel()
  }

  private[code] def run(): Unit = start(execute) { commands =>
    val comparison = target.map(TurtleDrawingGrading.assess(commands, _, comparisonPolicy))
    scenes = Vector((target, comparison) match {
      case (Some(_), Some(result)) => result.scene
      case _ => TurtleDrawingGrading.drawingScene(commands)
    })
    Status.Ready(commands, comparison.map(_.matches))
  }

  private def start[A](execute: () => Future[A])(completed: A => Status): Unit = {
    if status.now() == Status.Running then return
    Try(prepare()) match {
      case Failure(error) =>
        reset()
        status.set(Status.Failed(Option(error.getMessage).getOrElse("This draft cannot be run.")))
        return
      case _ => ()
    }
    revision += 1
    val requested = revision
    val original = source.now()
    scenes = Vector.empty
    status.set(Status.Running)
    if requested != revision || status.now() != Status.Running then return
    Try(execute()).fold(Future.failed, identity).onComplete {
      case _ if requested != revision || original != source.now() => ()
      case Success(result) =>
        Try(completed(result)) match {
          case Success(next) => status.set(next)
          case Failure(error) =>
            scenes = Vector.empty
            status.set(Status.Failed(Option(error.getMessage).getOrElse("The drawing could not be compared.")))
        }
      case Failure(_: CancellationException) => status.set(Status.Stopped)
      case Failure(error) => status.set(Status.Failed(Option(error.getMessage).getOrElse("Your program could not finish.")))
    }
  }

  private[code] def stop(): Unit = if status.now() == Status.Running then {
    revision += 1
    scenes = Vector.empty
    status.set(Status.Stopped)
    cancel()
  }

  private def picture(scene: TurtleJsxGraphRenderer.Scene, caption: String): Element =
    figure(cls := "turtle-execution__figure", figCaption(caption), TurtleJsxGraphRenderer.render(scene, caption))

  private lazy val domElement = div(
    cls := "turtle-execution",
    onMountCallback(_ => activate()),
    onUnmountCallback(_ => deactivate()),
    h3("Drawing"),
    div(cls := "turtle-execution__actions",
      button(typ := "button", disabled <-- status.signal.map(_ == Status.Running), "Run", onClick --> (_ => run())),
      button(typ := "button", disabled <-- status.signal.map(_ != Status.Running), "Stop", onClick --> (_ => stop()))
    ),
    p(cls := "turtle-execution__status", role := "status", aria.live := "polite",
      child.text <-- status.signal.map {
        case Status.Idle => "Run your program to see its drawing."
        case Status.Running => "Your program is running…"
        case Status.Stopped => "Stopped. You can edit your program and run it again."
        case Status.Ready(_, Some(true)) => "Your drawing matches the target. Nicely done!"
        case Status.Ready(_, Some(false)) => "Your drawing doesn't match the target yet. Check the distances and turns."
        case Status.Ready(_, None) => "Here's your drawing."
        case Status.Failed(_) => ""
      }),
    child.maybe <-- status.signal.map {
      case Status.Failed(message) => Some(p(cls := "turtle-execution__error", role := "alert", message))
      case _ => None
    },
    child.maybe <-- status.signal.map {
      case Status.Ready(_, _) =>
        Some(picture(scenes.head, if target.isDefined then "Target comparison" else "Your drawing"))
      case _ => target.map { graphic =>
        Try(TurtleDrawingGrading.targetScene(graphic, comparisonPolicy)).fold(
          error => p(cls := "turtle-execution__error", role := "alert", error.getMessage),
          scene => picture(scene, "Target"))
      }
    }
  )

  override def getDomElement(): Element = domElement
}
