package it.evadid.workbook.elements.interactionElements.matching

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus}
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future}

class MatchingInteractionSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  private def id(s: String) = LanguageMapContentId(s"test/$s")

  // Three statements, four categories; category 3 is a distractor and category 0 is used twice.
  private val matching = MatchingInteraction("matching", None, List(id("l0"), id("l1"), id("l2")),
    List(id("r0"), id("r1"), id("r2"), id("r3")), Some(List(0, 2, 0)))

  private def grade(state: MatchingInteractionState): Future[GradingResult[MatchingInteractionState, Int]] =
    matching.gradingElements.head.gradeStateSafe(state, true, UpdateImportance.MAJOR, TestExecutionContext())

  private def answered(selection: Option[Int]*): MatchingInteractionState = MatchingInteractionState(selection)

  test("connecting replaces, removes and ignores connections outside the task") {
    val first = matching.connect(matching.defaultValue, 0, Some(1))
    assertEquals(first, answered(Some(1), None, None))
    assertEquals(matching.connect(first, 0, Some(2)), answered(Some(2), None, None))
    assertEquals(matching.connect(first, 0, None), matching.defaultValue)
    assertEquals(matching.connect(first, 1, Some(9)), answered(Some(1), None, None))
    assertEquals(matching.connect(first, 5, Some(0)), answered(Some(1), None, None))
    matching.validateState(first)
    intercept[IllegalArgumentException](matching.validateState(answered(Some(1))))
    intercept[IllegalArgumentException](matching.validateState(answered(Some(7), None, None)))
  }

  test("grading counts correct connections and distinguishes all, some and none") {
    assertEquals(matching.gradingElements.size, 1)
    val states = List(answered(Some(0), Some(2), Some(0)), answered(Some(0), Some(3), None), matching.defaultValue,
      answered(Some(3), Some(3), Some(3)))
    Future.traverse(states)(grade).map { results =>
      assertEquals(results.map(_.additionalData), List(Some(3), Some(1), Some(0), Some(0)))
      assertEquals(results.map(_.gradingStatus),
        List(GradingStatus.Correct, GradingStatus.PartiallyCorrect, GradingStatus.Incorrect, GradingStatus.Incorrect))
    }
  }

  test("a task without expected answers has no grader and invalid answers are rejected") {
    assert(MatchingInteraction("free", None, List(id("l0")), List(id("r0")), None).gradingElements.isEmpty)
    intercept[IllegalArgumentException](MatchingInteraction("short", None, List(id("l0"), id("l1")), List(id("r0")), Some(List(0))))
    intercept[IllegalArgumentException](MatchingInteraction("range", None, List(id("l0")), List(id("r0")), Some(List(1))))
  }

  test("likert ratings reuse the matching state and allow clearing a row") {
    val likert = LikertScaleInteraction("likert", None, List(id("s0"), id("s1")), List(id("p0"), id("p1"), id("p2")))
    val rated = likert.rate(likert.rate(likert.defaultValue, 1, Some(2)), 0, Some(0))
    assertEquals(rated, answered(Some(0), Some(2)))
    assertEquals(likert.rate(rated, 1, None), answered(Some(0), None))
    assert(likert.gradingElements.isEmpty)
    intercept[IllegalArgumentException](likert.validateState(answered(Some(3), None)))
  }
}
