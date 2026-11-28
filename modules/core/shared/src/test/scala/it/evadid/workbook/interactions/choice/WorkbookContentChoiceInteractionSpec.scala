package it.evadid.workbook.interactions.choice

import it.evadid.core.util.UtcTime

import it.evadid.workbook.support.OfflineController
import it.evadid.workbook.sync.UpdateImportance

import it.evadid.core.datastructures.language.{LanguageMapContentId, WorkbookContent}
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.interactions.choice.WorkbookContentChoiceInteraction.*
import it.evadid.workbook.serialization.WorkbookElementFactory
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future}

class WorkbookContentChoiceInteractionSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private def id(s: String) = LanguageMapContentId(s"test/$s")
  private val options = (0 to 2).map(i => ChoiceOptionWorkbookContent(WorkbookContent(id(s"option$i")),
    Some(GradingForChoice(if i < 2 then GradingStatus.Correct else GradingStatus.Incorrect, id(s"feedback$i")))))
  private val single = WorkbookContentChoiceInteraction("single", ChoiceConfig(id("prompt"), Some(false), GraderRunningBehavior.RUN_ALWAYS), options)
  private val multiple = single.copy(elementId = "multi", config = single.config.copy(allowMultipleSelected = Some(true)))
  private def selected(e: WorkbookContentChoiceInteraction, indices: Int*) = indices.foldLeft(e.defaultValue)((s, i) => e.select(s, i, true))
  private def grade(e: WorkbookContentChoiceInteraction, state: ChoiceState) = e.gradingElements.head.gradeStateSafe(state, false, UpdateImportance.MAJOR, TestExecutionContext())

  test("single selection replaces the previous choice; stale unchecking is ignored") {
    val state = selected(single, 0, 1)
    assertEquals(state.selectedOptions, Seq(options(1)))
    assertEquals(single.select(state, 0, false), state)
    assert(!single.select(state, 1, false).isAnswered)
  }
  test("multiple choices retain selection and deselection events without duplicate selections") {
    val state = selected(multiple, 1, 0)
    assertEquals(state.selectedOptions.toSet, Set(options(0), options(1)))
    assertEquals(multiple.select(state, 0, true), state)
    assertEquals(multiple.select(state, 0, false).selectedOptions, Seq(options(1)))
  }
  test("the grader evaluates exact multiple choices and publishes the chosen option's content feedback") {
    for {
      right <- grade(single, selected(single, 0))
      wrong <- grade(single, selected(single, 2))
      empty <- grade(single, single.defaultValue)
      partial <- grade(multiple, selected(multiple, 1))
      complete <- grade(multiple, selected(multiple, 1, 0))
      extra <- grade(multiple, selected(multiple, 1, 0, 2))
    } yield {
      assertEquals(right.gradingStatus, GradingStatus.Correct)
      assertEquals(right.feedbackInformation, Some(WorkbookContent(id("feedback0"))))
      assertEquals(wrong.gradingStatus, GradingStatus.Incorrect)
      assertEquals(empty.gradingStatus, GradingStatus.Incorrect)
      assertEquals(partial.gradingStatus, GradingStatus.PartiallyCorrect)
      assertEquals(complete.gradingStatus, GradingStatus.Correct)
      assertEquals(extra.gradingStatus, GradingStatus.Incorrect)
    }
  }
  test("reflection choices record answers without inventing a grader") {
    val reflection = single.copy(options = options.map(_.copy(gradingForChoice = None)))
    assert(reflection.gradingElements.isEmpty)
    assert(selected(reflection, 0).isAnswered)
  }
  test("invalid choices and foreign restored states are rejected") {
    intercept[IllegalArgumentException](single.copy(options = Seq.empty))
    intercept[IllegalArgumentException](single.copy(options = options :+ options.head))
    intercept[IllegalArgumentException](single.copy(options = options.updated(0, options.head.copy(gradingForChoice = None))))
    intercept[IllegalArgumentException](single.select(single.defaultValue, -1, true))
    intercept[IllegalArgumentException](single.select(single.defaultValue, 3, true))
    intercept[IllegalArgumentException](single.validateState(selected(multiple, 0, 1)))
    val foreign = options.head.copy(content = WorkbookContent(id("foreign")))
    intercept[IllegalArgumentException](single.validateState(ChoiceState(Seq(ChoiceSelectedEvent(UtcTime.now(), foreign)))))
  }
  test("definitions, event states and grader results use the current registered codecs") {
    for (exercise <- Seq(single, multiple); serializer <- Seq(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(exercise)), exercise)
    val state = selected(multiple, 0, 1)
    val format = multiple.interactionFormat
    assertEquals(format.stateSerializer.deserialize(format.stateSerializer.serialize(state)), state)
    val answers = OfflineController.of(multiple)
    answers.setStateFromUserInteraction(multiple, state, it.evadid.workbook.sync.UpdateImportance.MAJOR)
    answers.feedback(multiple).getOrCalculateGrading(true).map { result =>
      val history = answers.currentHistory(multiple)
      val restored = format.historySerializer.deserialize(format.historySerializer.serialize(history))
      assertEquals(restored, history)
      assertEquals(restored.gradingForState(multiple.gradingElements, state).testsWithResults.head.gradingStatus, GradingStatus.Correct)
    }
  }
}
