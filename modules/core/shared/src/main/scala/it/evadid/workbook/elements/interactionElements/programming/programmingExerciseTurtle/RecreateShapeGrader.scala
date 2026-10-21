package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.workbook.abstractions.FeedbackEntity.TestEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.abstractions.grading.GraderRunningBehavior.RUN_ALWAYS
import it.evadid.workbook.abstractions.grading.GradingImportance.{OPTIONAL_TEST, REQUIRED_TEST}
import it.evadid.workbook.abstractions.grading.GradingStatus.{Correct, Incorrect, PartiallyCorrect}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGradingLogic.TurtleGraphicComparison
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGradingLogic.TurtleLineStatus.CORRECT
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class RecreateShapeGrader(desiredResult: TurtleGraphic, isRequired: Boolean) extends WorkbookInteractionGrader[ProgrammingState, Nothing] derives ReadWriter {

  override def gradeState(state: ProgrammingState, availableExecutionClients: List[ExecutionClient]): Future[GradingResult[ProgrammingState, Nothing]] = Future {

    val comp = TurtleGraphicComparison(TurtleGraphic(state.toBeExpressionState.deriveTurtleCommands), desiredResult)
    val incorrect = comp.difference.filter(_.status != CORRECT)
    val correct = comp.difference.filter(_.status == CORRECT)
    val grade = {
      if (incorrect.isEmpty) Correct()
      else if (correct.nonEmpty) PartiallyCorrect()
      else Incorrect()
    }
    GradingResult[ProgrammingState, Nothing](this,  state, grade)

  }(using ExecutionContext.global)


  override def importance: GradingImportance = if (isRequired) REQUIRED_TEST else OPTIONAL_TEST

  override def runningBehavior: GraderRunningBehavior = RUN_ALWAYS

  override def gradingEntity: FeedbackEntity = TestEntity()
}
