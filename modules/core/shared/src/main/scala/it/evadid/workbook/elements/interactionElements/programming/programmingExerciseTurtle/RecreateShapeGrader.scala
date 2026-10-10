package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import upickle.default.*

import scala.concurrent.Future

case class RecreateShapeGrader(desiredResult: TurtleGraphic) extends WorkbookInteractionGrader[ProgrammingState, RecreateShapeGradingResult] derives ReadWriter {

  override def gradeState(state: ProgrammingState): Future[RecreateShapeGradingResult] = {

    ???

  }
}
