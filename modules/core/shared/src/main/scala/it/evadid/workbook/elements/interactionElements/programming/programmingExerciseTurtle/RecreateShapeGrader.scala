package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.workbook.abstractions.grading.GraderRunningBehavior.RUN_ALWAYS
import it.evadid.workbook.abstractions.grading.GradingImportance.{OPTIONAL_TEST, REQUIRED_TEST}
import it.evadid.workbook.abstractions.grading.{GraderRunningBehavior, GradingStatus, GradingImportance, WorkbookInteractionGrader}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGradingLogic.TurtleGraphicComparison
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGradingLogic.TurtleLineStatus.CORRECT
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}

case class RecreateShapeGrader(desiredResult: TurtleGraphic, isRequired: Boolean) extends WorkbookInteractionGrader[ProgrammingState, RecreateShapeGradingResult] derives ReadWriter {

  override def gradeState(state: ProgrammingState): Future[RecreateShapeGradingResult] = Future {

    val comp = TurtleGraphicComparison(TurtleGraphic(state.toBeExpressionState.deriveTurtleCommands), desiredResult)
    val incorrect = comp.difference.filter(_.status != CORRECT)
    val correct = comp.difference.filter(_.status == CORRECT)
    val grade = {
      if (incorrect.isEmpty) GradingStatus.CORRECT
      else if (correct.nonEmpty) GradingStatus.PARTIALLY_CORRECT
      else GradingStatus.INCORRECT
    }
    RecreateShapeGradingResult(state, grade, None)

  }(using ExecutionContext.global)


  override def importance: GradingImportance = if (isRequired) REQUIRED_TEST else OPTIONAL_TEST

  override def runningBehavior: GraderRunningBehavior = RUN_ALWAYS
}
