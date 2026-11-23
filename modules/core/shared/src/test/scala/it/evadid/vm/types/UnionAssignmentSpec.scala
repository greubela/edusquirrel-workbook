package it.evadid.vm.types

import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.BeDataType.{AnyType, BeUnionAllowedTypes, ClassType}
import munit.FunSuite
import upickle.default.{read, write}

class UnionAssignmentSpec extends FunSuite {
  private val numberOrText = BeUnionAllowedTypes(Set(BeDataType.Int, BeDataType.String))
  private val box = ClassType(BeEntityName.fromCodeString("Box"))

  test("a union accepts values of an equal, narrower or overlapping union") {
    assertEquals(numberOrText.canTakeValuesFrom(BeUnionAllowedTypes(Set(BeDataType.String, BeDataType.Int))),
      AssigningPossibleWithSameType(numberOrText))
    assertEquals(numberOrText.canTakeValuesFrom(BeUnionAllowedTypes(Set(BeDataType.Int, BeDataType.Boolean))),
      AssigningPossibleWithImplicitCast(BeDataType.Int))
    assertEquals(numberOrText.canTakeValuesFrom(BeUnionAllowedTypes(Set(BeDataType.Date))), AssigningNotPossible())
    assertEquals(numberOrText.canTakeValuesFrom(AnyType), AssigningPossibleWithImplicitCast(numberOrText))
  }

  test("class types match only themselves, unions containing them and untyped values") {
    val withBox = BeUnionAllowedTypes(Set(box, BeDataType.Int))
    assertEquals(withBox.canTakeValuesFrom(box), AssigningPossibleWithImplicitCast(box))
    assertEquals(numberOrText.canTakeValuesFrom(box), AssigningNotPossible())
    assertEquals(box.canTakeValuesFrom(box), AssigningPossibleWithSameType(box))
    assertEquals(box.canTakeValuesFrom(AnyType), AssigningPossibleWithImplicitCast(box))
    assertEquals(box.canTakeValuesFrom(withBox), AssigningPossibleWithImplicitCast(box))
    assertEquals(box.canTakeValuesFrom(BeDataType.Int), AssigningNotPossible())
    assertEquals(BeDataType.Int.canTakeValuesFrom(box), AssigningNotPossible())
    assertEquals(BeDataType.Numeric.canTakeValuesFrom(AnyType), AssigningPossibleWithImplicitCast(BeDataType.Numeric))
    assertEquals(box.formatTypeForDisplay.getInLanguage(it.evadid.core.datastructures.language.AppLanguage.Python), "Box")
    assertEquals(box.formatValueForDisplay("b").getInLanguage(it.evadid.core.datastructures.language.AppLanguage.Python), "b")
    assert(!box.isValidLiteral("b"))
  }

  test("union codecs reject other data types and malformed JSON") {
    assertEquals(read[BeDataType.BeUnionType](write[BeDataType.BeUnionType](numberOrText)), numberOrText)
    intercept[Exception](read[BeDataType.BeUnionType](write[BeDataType](BeDataType.Int)))
    intercept[Exception](read[BeDataType]("[1]"))
    assertEquals(read[BeDataType](write[BeDataType](box)), box)
    assertEquals(AnyType.formatValueForDisplay("x").getInLanguage(it.evadid.core.datastructures.language.AppLanguage.Java), "x")
    assert(!AnyType.isValidLiteral("x"))
  }
}
