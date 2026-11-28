package it.evadid.workbook.reports

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import munit.FunSuite
import it.evadid.workbook.abstractions.grading.*
import GradingStatus.*
import it.evadid.workbook.interactions.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.interactions.programming.programmingExerciseRegular.ProgrammingUnitTestGrader
import it.evadid.workbook.interactions.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.distribution.command.SerializedException

class GradingSummarySpec extends FunSuite {
  private val state: ProgrammingState = ProgrammingStateRaw(Python, "forward(1)")
  private val graders = Seq("first", "second").map(id => ProgrammingUnitTestGrader(id, "assert True", GradingImportance.REQUIRED_TEST))
  private def graded(statuses: Seq[GradingStatus]): GradedState[ProgrammingState, AdditionalProgrammingTestInfo] =
    GradedState(state, graders.zip(statuses).map((grader, status) => grader -> GradingResult(grader, state, status)).toMap)

  test("one aggregate status governs pending, errors, correctness and mixed outcomes") {
    val error = TestError(SerializedException("private diagnostic"))
    Seq(
      (Seq(Correct, Correct), Correct),
      (Seq(Correct, Incorrect), PartiallyCorrect),
      (Seq(Incorrect, Incorrect), Incorrect),
      (Seq(PartiallyCorrect, Incorrect), Incorrect),
      (Seq(NotFinished, Correct), NotFinished),
      (Seq(NotRequested, NotFinished), NotRequested)
    ).foreach { (statuses, expected) =>
      val value = graded(statuses)
      val summary = GradingSummary(graders, value)
      assertEquals(summary.status, expected)
      assertEquals(value.totalEvaluation(), expected)
    }
    val summary = GradingSummary(graders, graded(Seq(error, Correct)))
    assert(summary.status.isError)
    assertEquals(summary.errors, 1)
    assertEquals(summary.retries, 1)
    assert(!summary.status.toString.contains("private diagnostic"))
  }

  test("missing authored results prevent an all-correct status and empty grading is unrequested") {
    val summary = GradingSummary(graders, graded(Seq(Correct)))
    assertEquals(summary.status, NotRequested)
    assertEquals(summary.correct, 1)
    assertEquals(summary.total, 2)
    assertEquals(summary.unavailable, 1)
    assertEquals(GradingSummary(Seq.empty, GradedState[ProgrammingState, AdditionalProgrammingTestInfo](state)).status, NotRequested)
  }

  test("stale and removed graders are excluded and duplicated authored keys count once") {
    val removed = ProgrammingUnitTestGrader("removed", "assert True", GradingImportance.REQUIRED_TEST)
    val stale = GradingResult(graders.last, ProgrammingStateRaw(Python, "old"), Correct)
    val value = graded(Seq(Correct)).copy(graderResultMap = graded(Seq(Correct)).graderResultMap ++
      Map(graders.last -> stale, removed -> GradingResult(removed, state, Correct)))
    val summary = GradingSummary(graders ++ graders, value)
    assertEquals(summary.correct, 1)
    assertEquals(summary.total, 2)
    assertEquals(summary.notRequested, 1)
    assertEquals(summary.status, NotRequested)
  }
}
