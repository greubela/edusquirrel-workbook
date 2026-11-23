package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay}
import it.evadid.workbook.abstractions.grading.GradingStatus
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future}

class SlideshowGraderSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private def slide(index: Int) = DisplayLangMapContent(s"slide-$index", LanguageMapContentId(s"test/slide$index"),
    LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.PLAINTEXT))
  private def deck = Slideshow("deck", (0 to 2).map(slide))
  test("unseen, partly viewed and completely viewed decks receive ordinary grading results") {
    val exercise = deck
    val first = exercise.defaultValue.recordTransitionByIndex(0, 0)
    val finished = first.recordTransitionByIndex(0, 1).recordTransitionByIndex(1, 2)
    Future.traverse(Seq(exercise.defaultValue, first, finished)) { state =>
      exercise.gradingElements.head.gradeStateSafe(state, false, TestExecutionContext()).map(_.gradingStatus)
    }.map(statuses => assertEquals(statuses, Seq(GradingStatus.Incorrect(), GradingStatus.PartiallyCorrect(), GradingStatus.Correct())))
  }
  test("one displayed slide completes a single-slide deck and records its timestamp by reference") {
    val exercise = Slideshow("single", Seq(slide(0)))
    val state = exercise.defaultValue.recordTransitionByIndex(0, 0)
    assertEquals(state.timestampsWhereUserSwitchedFromOrToPanel(slide(0)), state.events.map(_.proceededAt))
    exercise.gradingElements.head.gradeStateSafe(state, false, TestExecutionContext()).map(result => assertEquals(result.gradingStatus, GradingStatus.Correct()))
  }
  test("automatic grading is stored and round-trips with the slideshow format") {
    val exercise = deck
    val answer = exercise.defaultValue.recordTransitionByIndex(0, 1).recordTransitionByIndex(1, 2)
    exercise.interactionVariable.setStateFromUserInteraction(answer, MAJOR, context = TestExecutionContext())
    exercise.interactionVariable.control.getOrCalculateGrading(TestExecutionContext()).map { result =>
      assert(result.testsWithResults.forall(_.gradingStatus.isCorrect))
      val history = exercise.interactionVariable.currentHistory
      val serializer = exercise.interactionFormat.historySerializer
      assertEquals(serializer.deserialize(serializer.serialize(history)), history)
    }
  }
  test("a restored deck cannot remove unseen panels or refer to foreign slides") {
    val exercise = deck
    intercept[IllegalArgumentException](exercise.validateState(exercise.defaultValue.copy(allPanels = Seq.empty)))
    val other = slide(8).asRef
    intercept[IllegalArgumentException](exercise.validateState(exercise.defaultValue.copy(events = Set(
      SlideshowState.SlideshowProceededEvent(other, other, java.time.LocalDateTime.now())))))
  }
}
