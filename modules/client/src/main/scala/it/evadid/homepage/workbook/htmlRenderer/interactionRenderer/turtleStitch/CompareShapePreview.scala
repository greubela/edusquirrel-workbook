package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.DeferredRendering
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/** Compares the bound answer with the target, sharing the comparison with the exercise's grader.
  *
  * The board is redrawn only after the answer has rested (see [[DeferredRendering]]); all views of one
  * preview share its prepared assessment, while each mounted view owns and reuses one JSXGraph board.
  */
final case class CompareShapePreview(boundVar: Var[ProgrammingState], expected: TurtleGraphic, policy: TurtleDrawingPolicy,
    delay: FiniteDuration = DeferredRendering.defaultDelay) extends HtmlAppElement {

  private given ExecutionContext = ExecutionContext.global

  private def assess(state: ProgrammingState): TurtleDrawingGrading.Assessment = TurtleDrawingGrading.assess(state, expected, policy)

  private[turtleStitch] def assessment(state: ProgrammingState): Try[TurtleDrawingGrading.Assessment] = Try(assess(state))

  private lazy val rendering = new DeferredRendering(boundVar.signal, assess, delay)

  /** The last successful assessment stays drawn, each new one on a fresh board; a failure hides it behind its
    * message until the next success. */
  private def view(assessments: Signal[Try[TurtleDrawingGrading.Assessment]]): Element = {
    val scenes = assessments.scanLeft(_.toOption.map(_.scene))((last, next) => next.toOption.map(_.scene).orElse(last))
    div(
      child.maybe <-- assessments.map(_.failed.toOption.map(_.getMessage)).distinct.map(_.map(message =>
        div(cls := "turtle-preview__error", s"Error while trying to draw turtle: $message"))),
      div(display <-- assessments.map(assessment => if assessment.isSuccess then "" else "none"),
        child.maybe <-- scenes.distinct.map(_.map(scene => TurtleJsxGraphRenderer.render(scene, "Turtle drawing")))))
  }

  override def getDomElement(): L.Element =
    rendering.element(id => laminarHelper.plaintextStringSignal(id))(view)
}
