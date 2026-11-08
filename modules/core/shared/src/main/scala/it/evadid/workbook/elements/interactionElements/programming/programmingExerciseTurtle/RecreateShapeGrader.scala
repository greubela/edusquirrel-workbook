package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.FeedbackEntity.TestEntity
import it.evadid.workbook.abstractions.grading.{GradingResult, *}
import it.evadid.workbook.abstractions.grading.GraderRunningBehavior.RUN_ALWAYS
import it.evadid.workbook.abstractions.grading.GradingImportance.{OPTIONAL_TEST, REQUIRED_TEST}
import it.evadid.workbook.abstractions.grading.GradingStatus.{Correct, Incorrect, PartiallyCorrect}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGradingLogic.TurtleGraphicComparison
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGradingLogic.TurtleLineStatus.CORRECT
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.{AdditionalProgrammingTestInfo, ProgrammingTestGrader}
import it.evadid.workbook.meta.ExecutionContextInformation
import upickle.default.*

import scala.concurrent.Future

case class RecreateShapeGrader(desiredResult: TurtleGraphic, override val importance: GradingImportance, testId: String = "shape",
    comparisonPolicy: TurtleDrawingPolicy = TurtleDrawingPolicy.Strokes)
  extends ProgrammingTestGrader derives ReadWriter {
  require(testId != null && testId.nonEmpty && testId == testId.trim, "A programming test needs a nonempty ID.")

  override protected def handleGrading(
                                        state: ProgrammingState,
                                        contextInformation: ExecutionContextInformation
                                      ): Future[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]] = Future {

    val actual = TurtleGraphic(state.toBeExpression.deriveTurtleCommands)
    val comparison = TurtleGradingLogic.compare(actual, desiredResult, comparisonPolicy)
    GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](this, state, comparison.gradingStatus)
  }(using contextInformation.futureExecutionContext)


  override def runningBehavior: GraderRunningBehavior = RUN_ALWAYS

  override def gradingEntity: FeedbackEntity = TestEntity()

}
