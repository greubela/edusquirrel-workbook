package it.evadid.workbook.abstractions.grading

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.interactions.gpt.{TextInputLlmGrader, TextInputWithScaffoldingAndGrading}
import it.evadid.workbook.interactions.slideshow.SlideshowGrader
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
    assertEquals(SlideshowGrader(false).key, "sha256:e78ed6251c5fcce94479c1afd6f96fed4d265c8ebdbdd7b2dc999778cf7c3527")
  }

  test("a key is the grader's persisted type and parameters, not its class name") {
    assertEquals(SlideshowGrader(false).key, it.evadid.core.util.crypto.ContentIdentity.key(
      ujson.Obj("type" -> "SlideshowGrader", "parameters" -> ujson.Obj("isRequired" -> false))))
  }

  test("graders of one type differ by their parameters, so two unit tests get two keys") {
    import it.evadid.workbook.interactions.programming.programmingExerciseRegular.ProgrammingUnitTestGrader
    val first = ProgrammingUnitTestGrader("first", "assert f() == 1")
    val second = ProgrammingUnitTestGrader("second", "assert f() == 1")
    val otherCode = first.copy(pythonTestCode = "assert f() == 2")
    assertEquals(List(first, second, otherCode).map(_.key).distinct.size, 3)
    assertEquals(first.copy().key, first.key)
  }
}
