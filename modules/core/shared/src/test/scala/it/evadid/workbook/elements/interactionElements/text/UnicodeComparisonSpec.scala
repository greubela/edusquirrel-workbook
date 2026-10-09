package it.evadid.workbook.elements.interactionElements.text

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.model.text.UnicodeText
import munit.FunSuite

class UnicodeComparisonSpec extends FunSuite {
  test("ASCII positions, decimal codes and padded hexadecimal codes") {
    val result = UnicodeText.characters("Az\n")
    assertEquals(result.map(_.position), List(1, 2, 3))
    assertEquals(result.map(_.codePoint), List(65, 122, 10))
    assertEquals(result.map(_.hexadecimal), List("U+0041", "U+007A", "U+000A"))
    assert(result.forall(_.wellFormed))
    assertEquals(UnicodeText.characters(""), Nil)
  }
  test("Latin and Cyrillic lookalikes remain different") {
    val latin = UnicodeText.characters("paypal.com")
    val spoof = UnicodeText.characters("payраl.com") // U+0440, U+0430
    assertEquals(latin(3).codePoint, 0x70)
    assertEquals(spoof(3).codePoint, 0x440)
    assertEquals(spoof(4).codePoint, 0x430)
    assertEquals(latin.size, spoof.size)
    assertNotEquals(latin, spoof)
  }
  test("supplementary emoji is one code point rather than two UTF-16 units") {
    val result = UnicodeText.characters("A🌍B")
    assertEquals(result.map(_.codePoint), List(65, 0x1F30D, 66))
    assertEquals(result(1).glyph, "🌍")
    assertEquals(result(1).hexadecimal, "U+1F30D")
    assertEquals(result.map(_.position), List(1, 2, 3))
  }
  test("combining accents and invisible characters are not normalized or dropped") {
    assertEquals(UnicodeText.characters("e\u0301").map(_.codePoint), List(101, 769))
    assertEquals(UnicodeText.characters("é").map(_.codePoint), List(233))
    assertEquals(UnicodeText.characters("a\u200Bb\u202E").map(_.hexadecimal), List("U+0061", "U+200B", "U+0062", "U+202E"))
  }
  test("unpaired surrogates are retained and explicitly marked invalid") {
    val input = "\uD800x\uDC00\uD800\uD800\uDC00"
    val result = UnicodeText.characters(input)
    assertEquals(result.map(_.wellFormed), List(false, true, false, false, true))
    assertEquals(result.map(_.glyph).mkString, input)
    assertEquals(result.last.codePoint, 0x10000)
  }
  test("both exercise and answer serializers preserve raw text") {
    val initial = UnicodeComparisonAnswer(" Nein heißt Нет 🌍 ", "e\u0301\n")
    val e = UnicodeComparisonInteraction("unicode-test", LanguageMapContentId("test/title"), initial)
    assertEquals(e.defaultValue, initial)
    assertEquals(e.serializerInteractionContent.deserialize(e.serializerInteractionContent.serialize(initial)), initial)
    for serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike) do
      assertEquals(serializer.deserialize(serializer.serialize(e)), e)
  }
  test("boundaries apply on construction and loading saved answers") {
    val e = UnicodeComparisonInteraction("bounded", LanguageMapContentId("test/title"))
    assertEquals(UnicodeComparisonAnswer("x" * 256).first.length, 256)
    intercept[IllegalArgumentException](UnicodeComparisonAnswer("x" * 257))
    intercept[IllegalArgumentException](UnicodeComparisonAnswer(second = "x" * 257))
    intercept[upickle.core.TraceVisitor.TraceException](e.serializerInteractionContent.deserialize(s"{\"first\":\"${"x" * 257}\",\"second\":\"\"}"))
  }
}
