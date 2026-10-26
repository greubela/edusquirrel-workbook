package it.evadid.vm.test

import it.evadid.workbook.elements.interactionElements.programming.ProgrammingTestGrader
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingUnitTestGrader
import upickle.default.ReadWriter

sealed trait BeTestSuite derives ReadWriter {
  def graders: List[ProgrammingTestGrader]
}

case class ProgrammingTestSuite(graders: List[ProgrammingTestGrader]) extends BeTestSuite derives ReadWriter {
  require(graders != null && graders.forall(grader => grader != null &&
    grader.testId != null && grader.testId.nonEmpty && grader.testId == grader.testId.trim),
    "Programming tests need nonempty IDs.")
  require(graders.map(_.testId).distinct.size == graders.size && graders.distinct.size == graders.size,
    "Programming test IDs must be unique.")
}

case class SampleBeTest(pythonTestCode: String) extends BeTestSuite derives ReadWriter {
  override def graders: List[ProgrammingTestGrader] =
    List(ProgrammingUnitTestGrader(ProgrammingUnitTestGrader.LegacyTestId, pythonTestCode))
}

