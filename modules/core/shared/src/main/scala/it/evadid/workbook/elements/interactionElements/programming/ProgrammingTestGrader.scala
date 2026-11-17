package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import upickle.default.{ReadWriter, readwriter}

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
trait ProgrammingTestGrader extends WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo] {
  def testId: String
  def publishProgramObservation: Boolean = false
}

object ProgrammingTestGrader {

}
