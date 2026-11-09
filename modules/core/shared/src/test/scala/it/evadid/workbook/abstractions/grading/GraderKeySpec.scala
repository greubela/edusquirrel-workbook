package it.evadid.workbook.abstractions.grading

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.elements.interactionElements.gpt.{TextInputLlmGrader, TextInputWithScaffoldingAndGrading}
import it.evadid.workbook.elements.interactionElements.slideshow.SlideshowGrader
import upickle.default.*

class GraderKeySpec extends munit.FunSuite {
  test("identical content has the same key regardless of instance, variable or list position") {
    val first = TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(LanguageMapContentId("test/first"), Map.empty)
    val second = first.copy(instructionForLlm = LanguageMapContentId("test/second"))
    val exercise = TextInputWithScaffoldingAndGrading("answer", LanguageMapContentId("test/task"), contentGrading = List(first, second, first))
    val keys = exercise.gradingElements.map(_.key)
    assertEquals(keys.head, keys.last)
    assert(keys.head != keys(1))
    assertEquals(exercise.copy(elementId = "other").gradingElements.map(_.key), keys)
    assertEquals(exercise.copy(contentGrading = List(second, first)).gradingElements.map(_.key), List(keys(1), keys.head))
    val grader = exercise.gradingElements.head
    assertEquals(read[TextInputLlmGrader](write(grader)).key, grader.key)
  }

  test("map iteration order does not affect the content identity") {
    val one = LanguageMapContentId("test/one")
    val two = LanguageMapContentId("test/two")
    val criteria = TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(one, scala.collection.immutable.ListMap(one -> two, two -> one))
    val grader = TextInputLlmGrader(one, None, criteria)
    val reordered = grader.copy(gradeData = criteria.copy(goodExampleInteractions = scala.collection.immutable.ListMap(two -> one, one -> two)))
    assertEquals(grader.key, reordered.key)
    assert(grader.copy(exerciseText = two).key != grader.key)
  }

  test("content identity uses the same SHA-256 vector on JVM and JavaScript") {
    assertEquals(SlideshowGrader(false).key, "sha256:36531a7273a1bd39bf8a2c70151701c6310dad7d2f7452005f212995e653ae52")
  }
}
