package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.{TurtleDrawingGrading, TurtleJsxGraphRenderer}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}

import java.util.concurrent.CancellationException
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}

final class TurtleExecutionPanel(
    source: Var[ProgrammingState],
    execute: () => Future[List[TurtleCommand[Double]]],
    cancel: () => Unit,
    target: Option[TurtleGraphic] = None,
    comparisonPolicy: TurtleDrawingPolicy = TurtleDrawingPolicy.Coverage
) extends HtmlAppElement {

  private[code] val status = Var[TurtleExecutionPanelStatus](TurtleExecutionPanelStatus.Idle)
  private val revision = Var(0L)
  private val scenes = Var(Vector.empty[TurtleJsxGraphRenderer.Scene])

  private[code] def reset(): Unit = {
    revision.update(_ + 1)
    val running = status.now() == TurtleExecutionPanelStatus.Running
    scenes.set(Vector.empty)
    status.set(TurtleExecutionPanelStatus.Idle)
    if running then cancel()
  }

  private[code] def run(): Unit = start(execute) { commands =>
    val comparison = target.map(TurtleDrawingGrading.assess(commands, _, comparisonPolicy))
    scenes.set(Vector((target, comparison) match {
      case (Some(_), Some(result)) => result.scene
      case _ => TurtleDrawingGrading.drawingScene(commands)
    }))
    TurtleExecutionPanelStatus.Ready(commands, comparison.map(_.matches))
  }

  private def start[A](execute: () => Future[A])(completed: A => TurtleExecutionPanelStatus): Unit = {
    if status.now() == TurtleExecutionPanelStatus.Running then return
    revision.update(_ + 1)
    val requested = revision.now()
    val original = source.now()
    scenes.set(Vector.empty)
    status.set(TurtleExecutionPanelStatus.Running)
    if requested != revision.now() || status.now() != TurtleExecutionPanelStatus.Running then return
    Try(execute()).fold(Future.failed, identity).onComplete {
      case _ if requested != revision.now() || original != source.now() => ()
      case Success(result) =>
        Try(completed(result)) match {
          case Success(next) => status.set(next)
          case Failure(error) =>
            scenes.set(Vector.empty)
            status.set(TurtleExecutionPanelStatus.Failed(Option(error.getMessage).getOrElse("The drawing could not be compared.")))
        }
      case Failure(_: CancellationException) => status.set(TurtleExecutionPanelStatus.Stopped)
      case Failure(error) => status.set(TurtleExecutionPanelStatus.Failed(Option(error.getMessage).getOrElse("Your program could not finish.")))
    }
  }

  private[code] def stop(): Unit = if status.now() == TurtleExecutionPanelStatus.Running then {
    revision.update(_ + 1)
    scenes.set(Vector.empty)
    status.set(TurtleExecutionPanelStatus.Stopped)
    cancel()
  }

  private def picture(scene: TurtleJsxGraphRenderer.Scene, caption: String): Element =
    figure(cls := "turtle-execution__figure", figCaption(caption),
      div(cls := "turtle-execution__canvas", TurtleJsxGraphRenderer.render(scene, caption)))

  private lazy val domElement = div(
    cls := "turtle-execution",
    onMountBind(_ => source.signal.distinct.changes --> (_ => reset())),
    onUnmountCallback(_ => reset()),
    h3("Drawing"),
    div(cls := "turtle-execution__actions",
      button(typ := "button", disabled <-- status.signal.map(_ == TurtleExecutionPanelStatus.Running), "Run", onClick --> (_ => queue.execute(() => run()))),
      button(typ := "button", disabled <-- status.signal.map(_ != TurtleExecutionPanelStatus.Running), "Stop", onClick --> (_ => stop()))
    ),
    p(cls := "turtle-execution__status", role := "status", aria.live := "polite",
      child.text <-- status.signal.map {
        case TurtleExecutionPanelStatus.Idle => "Run your program to see its drawing."
        case TurtleExecutionPanelStatus.Running => "Your program is running…"
        case TurtleExecutionPanelStatus.Stopped => "Stopped. You can edit your program and run it again."
        case TurtleExecutionPanelStatus.Ready(_, Some(true)) => "Your drawing matches the target. Nicely done!"
        case TurtleExecutionPanelStatus.Ready(_, Some(false)) => "Your drawing doesn't match the target yet. Check the distances and turns."
        case TurtleExecutionPanelStatus.Ready(_, None) => "Here's your drawing."
        case TurtleExecutionPanelStatus.Failed(_) => ""
      }),
    child.maybe <-- status.signal.map {
      case TurtleExecutionPanelStatus.Failed(message) => Some(p(cls := "turtle-execution__error", role := "alert", message))
      case _ => None
    },
    child.maybe <-- status.signal.map {
      case TurtleExecutionPanelStatus.Ready(_, _) =>
        Some(picture(scenes.now().head, if target.isDefined then "Target comparison" else "Your drawing"))
      case _ => None
    },
    // Every edit resets the status; only a change between result and target may rebuild the (costly) target board.
    child.maybe <-- status.signal.map(_.isInstanceOf[TurtleExecutionPanelStatus.Ready]).distinct.map(ready => if ready then None else targetPicture)
  )

  private lazy val targetPicture: Option[Element] = target.map { graphic =>
    Try(TurtleDrawingGrading.targetScene(graphic, comparisonPolicy)).fold(
      error => p(cls := "turtle-execution__error", role := "alert", error.getMessage),
      scene => picture(scene, "Target"))
  }

  override def getDomElement(): Element = domElement
}
