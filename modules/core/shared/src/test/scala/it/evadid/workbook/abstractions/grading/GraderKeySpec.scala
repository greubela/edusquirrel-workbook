package it.evadid.workbook.abstractions.grading

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading

class GraderKeySpec extends munit.FunSuite {
  test("equal grader definitions at different list positions have distinct stable variable keys") {
    val criterion = TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(LanguageMapContentId("test/criterion"), Map.empty)
    val exercise = TextInputWithScaffoldingAndGrading("answer", LanguageMapContentId("test/task"),
      contentGrading = List(criterion, criterion))
    val keys = exercise.gradingElements.map(_.key)
    assertEquals(keys, List("answer_history:0", "answer_history:1"))
    assertEquals(exercise.copy().gradingElements.map(_.key), keys)
    assertEquals(exercise.copy(elementId = "other").gradingElements.map(_.key), List("other_history:0", "other_history:1"))
  }
}
