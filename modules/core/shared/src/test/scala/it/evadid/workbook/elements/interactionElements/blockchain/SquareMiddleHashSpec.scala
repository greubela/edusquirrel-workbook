package it.evadid.workbook.elements.interactionElements.blockchain

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.model.blockchain.SquareMiddleHash
import munit.FunSuite

class SquareMiddleHashSpec extends FunSuite {
  private val title = LanguageMapContentId("test/hash")
  private val collision = SquareMiddleHashInteraction("collision", title, FindHashCollision())
  test("even and odd square lengths follow the source's left-biased rule") {
    assertEquals(SquareMiddleHash.calculate(57).highlightedSquare, "3[24]9")
    assertEquals(SquareMiddleHash.calculate(23).highlightedSquare, "[52]9")
    assertEquals(SquareMiddleHash.calculate(100).highlightedSquare, "1[00]00")
    assertEquals(SquareMiddleHash.calculate(4).hash, "16")
  }
  test("the three source worksheet examples are calculated exactly") {
    assertEquals(List(42, 99, 13).map(n => SquareMiddleHash.calculate(BigInt(n)).hash), List("76", "80", "16"))
    assertEquals(SquareMiddleHash.calculate(45).hash, "02")
    assertEquals(SquareMiddleHash.calculate(35).square, BigInt(1225))
    assertEquals(SquareMiddleHash.calculate(35).hash, "22")
  }
  test("large integers remain exact on JVM and JavaScript without floating point") {
    val number = BigInt("123456789012345678901234567890")
    assertEquals(SquareMiddleHash.calculate(number).square.toString,
      "15241578753238836750495351562536198787501905199875019052100")
    assertEquals(SquareMiddleHash.calculate(BigInt(10).pow(40)).hash, "00")
  }
  test("invalid numeric drafts are rejected without exceptions and valid spelling is normalized") {
    for (value <- List("", " ", "3", "0", "-4", "4.0", "1e3", "abc", "٤", "9" * 101))
      assertEquals(SquareMiddleHash.parse(value), None)
    assertEquals(SquareMiddleHash.parse(" 0057 ").map(_.input), Some(BigInt(57)))
    assert(SquareMiddleHash.parse("9" * 100).nonEmpty)
    intercept[IllegalArgumentException](SquareMiddleHash.calculate(3))
  }
  test("collision grading accepts different valid inputs with equal hashes, not equal numeric values") {
    assertEquals(collision.isCorrect(SquareMiddleHashAnswer("35", "65")), Some(true))
    assertEquals(collision.isCorrect(SquareMiddleHashAnswer("35", "035")), Some(false))
    assertEquals(collision.isCorrect(SquareMiddleHashAnswer("35", "45")), Some(false))
    assertEquals(collision.isCorrect(SquareMiddleHashAnswer("35", "")), Some(false))
  }
  test("preimages accept alternative solutions and preserve a leading zero target") {
    val preimage = collision.copy(task = FindHashPreimage("22"))
    assertEquals(preimage.isCorrect(SquareMiddleHashAnswer("35")), Some(true))
    assertEquals(preimage.isCorrect(SquareMiddleHashAnswer("65")), Some(true))
    assertEquals(preimage.isCorrect(SquareMiddleHashAnswer("45")), Some(false))
    assertEquals(preimage.copy(task = FindHashPreimage("02")).isCorrect(SquareMiddleHashAnswer("45")), Some(true))
    assertEquals(preimage.copy(task = FindHashPreimage("77")).isCorrect(SquareMiddleHashAnswer("76")), Some(true))
    assertEquals(preimage.results(SquareMiddleHashAnswer("65")).size, 1)
    intercept[IllegalArgumentException](FindHashPreimage("2"))
    intercept[IllegalArgumentException](FindHashPreimage("ab"))
  }
  test("exploration remains ungraded and invalid drafts round-trip as drafts") {
    val e = collision.copy(task = ExploreSquareMiddleHash())
    val draft = SquareMiddleHashAnswer("", "4.5")
    assertEquals(e.isCorrect(draft), None)
    assertEquals(e.results(draft), List(None, None))
    assertEquals(e.serializerInteractionContent.deserialize(e.serializerInteractionContent.serialize(draft)), draft)
    intercept[IllegalArgumentException](SquareMiddleHashAnswer("1" * 101))
  }
  test("all tasks and their initial values round-trip through both definition formats") {
    for (task <- List(ExploreSquareMiddleHash(), FindHashCollision(), FindHashPreimage("00"));
         serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike)) {
      val e = collision.copy(task = task, initial = SquareMiddleHashAnswer("57", "23"))
      assertEquals(serializer.deserialize(serializer.serialize(e)), e)
    }
  }
}
