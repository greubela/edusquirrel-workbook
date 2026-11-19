package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleDrawingPolicy.Strokes
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

case class CompareShapePreview(boundVar: Var[ProgrammingState], expected: TurtleGraphic) extends HtmlAppElement {

  given ec: ExecutionContext = ExecutionContext.global

  private def generateAssessmentFromState(state: ProgrammingState): Try[TurtleDrawingGrading.Assessment] = Try {
    val commands = state.match {
      case java @ ProgrammingStateRaw(Java, _) => java.toLegacyTurtleCommands.map(_.asDouble)
      case _: ProgrammingState => state.toBeExpression.turtleCommandExecutionInfo.completedCommands.map(_.asDouble)
    }
    TurtleDrawingGrading.assess(commands, expected, Strokes)
  }

  def createNewRendering(assTry: Try[TurtleDrawingGrading.Assessment]): Element = {
    assTry.match {
      case Success(assessment) => TurtleJsxGraphRenderer.render(assessment.scene, "Turtle drawing")
      case Failure(exception) => div(s"Error while trying to draw turtle: ${exception.getMessage}")
    }
  }

  lazy val comparisonSignal: Signal[Try[TurtleDrawingGrading.Assessment]] = boundVar.signal.map(generateAssessmentFromState)

  override def getDomElement(): L.Element = div(child <-- comparisonSignal.map(createNewRendering))


}
