package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.DeferredRendering
import it.evadid.homepage.webElements.editor.code.program.ProgramAnalyzer
import it.evadid.vm.analysis.ProgramAnalysis
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Try}

/** Compares the bound answer with the target as the exercise's grader does.
  *
  * The board is redrawn only after the answer has rested (see [[DeferredRendering]]). The program's turtle
  * commands come from the shared analysis worker, so parsing and interpreting never block the page; only the
  * comparison with the target runs here. All views of one preview share its prepared assessment, while each
  * mounted view owns and reuses one JSXGraph board.
  */
final case class CompareShapePreview(source: Signal[ProgrammingState], expected: TurtleGraphic, policy: TurtleDrawingPolicy,
    environment: ProgrammingEnvironment, delay: FiniteDuration = DeferredRendering.defaultDelay, analyzer: ProgramAnalyzer = ProgramAnalyzer.shared) extends HtmlAppElement {

  private given ExecutionContext = ExecutionContext.global

  private[turtleStitch] def assessment(analysis: ProgramAnalysis): Try[TurtleDrawingGrading.Assessment] =
    analysis.completedCommands.fold(reason => Failure(IllegalArgumentException(reason)),
      commands => Try(TurtleDrawingGrading.assess(commands, expected, policy)))

  private lazy val rendering = new DeferredRendering[ProgrammingState, TurtleDrawingGrading.Assessment](source,
    state => analyzer.analyse(state, environment).flatMap(analysis => Future.fromTry(assessment(analysis))), delay)

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
