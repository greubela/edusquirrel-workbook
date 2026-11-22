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
import scala.util.{Failure, Success, Try}

/** Compares the bound answer with the target, sharing the comparison with the exercise's grader.
  *
  * The board is redrawn only after the answer has rested (see [[DeferredRendering]]); all views of one
  * preview share its prepared assessment, while each mounted view owns its JSXGraph board.
  */
final case class CompareShapePreview(boundVar: Var[ProgrammingState], expected: TurtleGraphic, policy: TurtleDrawingPolicy,
    delay: FiniteDuration = DeferredRendering.defaultDelay) extends HtmlAppElement {

  private given ExecutionContext = ExecutionContext.global

  private def assess(state: ProgrammingState): TurtleDrawingGrading.Assessment = TurtleDrawingGrading.assess(state, expected, policy)

  private[turtleStitch] def assessment(state: ProgrammingState): Try[TurtleDrawingGrading.Assessment] = Try(assess(state))

  private lazy val rendering = new DeferredRendering(boundVar.signal, assess, delay)

  private def view(assessment: Try[TurtleDrawingGrading.Assessment]): Element = assessment match {
    case Success(value) => TurtleJsxGraphRenderer.render(value.scene, "Turtle drawing")
    case Failure(exception) => div(cls := "turtle-preview__error", s"Error while trying to draw turtle: ${exception.getMessage}")
  }

  override def getDomElement(): L.Element =
    rendering.element(id => laminarHelper.plaintextStringSignal(id))(view)
}
