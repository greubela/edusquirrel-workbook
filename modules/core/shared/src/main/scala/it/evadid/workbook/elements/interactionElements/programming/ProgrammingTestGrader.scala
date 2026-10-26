package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import upickle.default.{ReadWriter, readwriter}

trait ProgrammingTestGrader extends WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo] {
  def testId: String
}

object ProgrammingTestGrader {
  given ReadWriter[ProgrammingTestGrader] =
    readwriter[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]].bimap(
      grader => grader,
      {
        case grader: ProgrammingTestGrader => grader
        case _ => throw IllegalArgumentException("This grader is not a programming test.")
      })
}
