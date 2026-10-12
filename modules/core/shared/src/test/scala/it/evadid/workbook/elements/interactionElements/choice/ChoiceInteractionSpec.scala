package it.evadid.workbook.elements.interactionElements.choice

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class ChoiceInteractionSpec extends FunSuite {
  private def id(s: String) = LanguageMapContentId(s"test/$s")
  private val single = ChoiceInteraction("single", id("prompt"), List(id("a"), id("b")), expected = Some(List(1)))
  private val multiple = single.copy(elementId = "multi", allowMultiple = true, expected = Some(List(0, 1)))
  test("single selection replaces previous choice; unchecking a stale option preserves the current choice") {
    val answer = single.select(single.select(ChoiceAnswer(), 0, true), 1, true)
    assertEquals(answer.selected, List(1))
    assertEquals(single.select(answer, 0, false), answer)
    assertEquals(single.select(answer, 1, false), ChoiceAnswer())
  }
  test("multiple selection is canonical, idempotent and removable") {
    val answer = multiple.select(multiple.select(ChoiceAnswer(), 1, true), 0, true)
    assertEquals(answer.selected, List(0, 1))
    assertEquals(multiple.select(answer, 0, true), answer)
    assertEquals(multiple.select(answer, 0, false).selected, List(1))
  }
  test("grading requires exactly the expected choices, including no extras or missing answers") {
    assertEquals(single.isCorrect(ChoiceAnswer()), Some(false))
    assertEquals(single.isCorrect(ChoiceAnswer(List(1))), Some(true))
    assertEquals(single.isCorrect(ChoiceAnswer(List(0))), Some(false))
    assertEquals(multiple.isCorrect(ChoiceAnswer(List(1))), Some(false))
    assertEquals(multiple.isCorrect(ChoiceAnswer(List(1, 0))), Some(true))
    assertEquals(single.isCorrect(ChoiceAnswer(List(0, 1))), Some(false))
    assertEquals(single.isCorrect(ChoiceAnswer(List(2))), Some(false))
  }
  test("reflection records a response without assigning a correct answer") {
    val reflection = single.copy(expected = None)
    assertEquals(reflection.isCorrect(ChoiceAnswer(List(0))), None)
    assert(!reflection.isAnswered(ChoiceAnswer()))
    assert(reflection.isAnswered(ChoiceAnswer(List(0))))
  }
  test("invalid configuration and selection indices are rejected") {
    intercept[IllegalArgumentException](single.copy(options = Nil))
    intercept[IllegalArgumentException](single.copy(expected = Some(List(2))))
    intercept[IllegalArgumentException](single.copy(expected = Some(List(0, 1))))
    intercept[IllegalArgumentException](single.select(ChoiceAnswer(), -1, true))
    intercept[IllegalArgumentException](single.select(ChoiceAnswer(), 2, true))
    intercept[IllegalArgumentException](ChoiceAnswer(List(1, 1)))
    intercept[IllegalArgumentException](ChoiceAnswer(List(-1)))
  }
  test("exercise definition round-trips both registered workbook formats") {
    for (e <- List(single, multiple, single.copy(expected = None)); serializer <- List(
      WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike)) {
      assertEquals(serializer.deserialize(serializer.serialize(e)), e)
    }
  }
  test("answer serialization preserves choices independently of exercise configuration") {
    val answer = ChoiceAnswer(List(1))
    assertEquals(single.serializerInteractionContent.deserialize(single.serializerInteractionContent.serialize(answer)), answer)
  }
}
