package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.steps

import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.basic.LabelSelection
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.DeferredRendering
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.{LineResult, RenderedAngle, RenderedLine, Scene}
import it.evadid.homepage.webElements.editor.code.program.ProgramAnalyzer
import it.evadid.vm.analysis.ProgramAnalysis
import it.evadid.vm.simulation.turtle.TurtleExecutionStatus
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.TurtleGraphic
import it.evadid.workbook.interactions.programming.state.ProgrammingState

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/**
 * Replays how the turtle drawing of the central answer state is created, one command per step, on the
 * same JSXGraph board as the comparison panel: earlier strokes are drawn, the current step's strokes are
 * highlighted, later strokes stay faint (so the view keeps its bounds), and a marker shows the turtle.
 */
final class TurtleStepPanel(source: Signal[ProgrammingState], text: String => Signal[String], environment: ProgrammingEnvironment,
    delay: FiniteDuration = DeferredRendering.defaultDelay, analyzer: ProgramAnalyzer = ProgramAnalyzer.shared, frame: Seq[Point[Double]] = Nil) extends HtmlAppElement {
  private given ExecutionContext = ExecutionContext.global

  // The shared analysis worker runs the program once the answer has rested; step navigation stays immediate.
  private val rendering = new DeferredRendering[ProgrammingState, Either[String, ProgramAnalysis.TurtleRun]](
    source, value => analyzer.analyse(value, environment).map(_.turtle), delay)

  private val executions: Signal[Option[Either[String, ProgramAnalysis.TurtleRun]]] =
    rendering.result.map(_.map(_.toEither.left.map(error => Option(error.getMessage).getOrElse(error.getClass.getSimpleName)).flatten))

  private val commands: Signal[Vector[TurtleCommand[Double]]] =
    executions.map(_.flatMap(_.toOption).fold(Vector.empty)(_.commands.toVector)).distinct

  private val navigator = new StepNavigator(commands.map(_.size), text)

  override def getDomElement(): Element = div(cls := "step-panel turtle-step-panel",
    rendering.status(id => text(id.entryKey)),
    // Each program and step gets a fresh board in its default view.
    TurtleJsxGraphRenderer.render(commands.combineWith(navigator.step).map((all, step) => TurtleStepPanel.scene(all, step).copy(frame = frame)), "Turtle steps"),
    navigator.element,
    p(cls := "step-panel__caption", aria.live := "polite", child.text <-- LabelSelection.formatted(commands.combineWith(navigator.step), Seq("turtleStepStart", "stepCommand"), text) { (all, step) =>
      Some(if step == 0 then "turtleStepStart" -> Nil
        else "stepCommand" -> Seq("step" -> step.toString, "command" -> TurtleStepPanel.describe(all(step - 1))))
    }),
    child.maybe <-- executions.map {
      case Some(Left(message)) => Some(p(cls := "step-panel__problem", child.text <-- text("programDataUnavailable").map(prefix => s"$prefix $message")))
      case Some(Right(run)) => run.status match {
        case TurtleExecutionStatus.Failed(message) => Some(p(cls := "step-panel__problem", child.text <-- text("programDataFailed").map(prefix => s"$prefix $message")))
        case TurtleExecutionStatus.LimitExceeded => Some(p(cls := "step-panel__problem", child.text <-- text("programDataLimit")))
        case _ => None
      }
      case None => None
    })
}

object TurtleStepPanel {
  /** Strokes of the first `step` commands, with the strokes of command `step` highlighted and later ones faint. */
  def scene(commands: Seq[TurtleCommand[Double]], step: Int): Scene = {
    def movements(count: Int) = TurtleGraphic.TurtleGraphicProgram(commands.take(count)).renderMovements
    val drawn = movements(step)
    val before = if step == 0 then Nil else movements(step - 1)
    val newFrom = if drawn.startsWith(before) then before.size else drawn.size
    val all = TurtleGraphic.TurtleGraphicProgram(commands).renderMovements
    val upcoming = if all.startsWith(drawn) then all.drop(drawn.size) else Nil
    val lines = drawn.zipWithIndex.map { (movement, index) =>
      RenderedLine(movement.start, movement.end, if index >= newFrom then LineResult.Current else LineResult.Neutral, movement.jump)
    } ++ upcoming.map(movement => RenderedLine(movement.start, movement.end, LineResult.Upcoming, movement.jump))
    val angles = TurtleGraphic.TurtleGraphicProgram(commands.take(step)).renderAngles.map(angle =>
      RenderedAngle(angle.vertex, angle.fromHeading, angle.degrees, angle.lineBefore, angle.lineAfter))
    Scene(lines, angles, Some(drawn.lastOption.fold(Point(0.0, 0.0))(_.end)))
  }

  def describe(command: TurtleCommand[Double]): String = {
    def number(value: Double) = if value.isWhole then value.toLong.toString else value.toString
    val arguments = command.args.map(number) ++ command.stringArgs.map(text => s"\"$text\"")
    s"${command.name.universalInterpretation()}(${arguments.mkString(", ")})"
  }
}
