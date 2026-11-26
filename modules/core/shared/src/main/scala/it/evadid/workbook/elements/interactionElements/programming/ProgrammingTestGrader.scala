package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import upickle.default.{ReadWriter, readwriter}

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
trait ProgrammingTestGrader extends WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo] {
  def testId: String
  def publishProgramObservation: Boolean = false

  /** This test in an exercise providing `environment`'s libraries: tests that read the program statically bind its
    * library calls in it; calls into other libraries are the program's unknown dependency.
    */
  def inEnvironment(environment: ProgrammingEnvironment): ProgrammingTestGrader
}

object ProgrammingTestGrader {
  /** The exercise's graders, with every programming test reading the program in `environment`. */
  def inEnvironment[GA](graders: Seq[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]],
      environment: ProgrammingEnvironment): Seq[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]] =
    graders.map {
      case test: ProgrammingTestGrader => test.inEnvironment(environment)
      case other => other
    }

  given ReadWriter[ProgrammingTestGrader] =
    readwriter[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]].bimap(
      grader => grader,
      {
        case grader: ProgrammingTestGrader => grader
        case _ => throw IllegalArgumentException("This grader is not a programming test.")
      })
}
