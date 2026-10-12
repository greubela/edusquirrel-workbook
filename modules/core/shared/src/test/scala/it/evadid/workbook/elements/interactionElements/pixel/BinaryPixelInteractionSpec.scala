package it.evadid.workbook.elements.interactionElements.pixel

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.model.pixel.*
import munit.FunSuite
import upickle.default.*

class BinaryPixelInteractionSpec extends FunSuite {
  private def id(s: String) = LanguageMapContentId(s"test/$s")
  private val seven = BinaryPixelImage.fromRows(List("111", "001", "001", "001", "001"))
  private val top = PixelThresholdProbe(id("top"), List(PixelPosition(0, 0), PixelPosition(0, 1), PixelPosition(0, 2)), 3)
  private val exercise = BinaryPixelInteraction("pixels", id("title"), BinaryPixelImage.blank(5, 3),
    Some(seven), List(top), List(PixelPreset(id("seven"), seven)))

  test("row-major indexing and toggling affect only the selected pixel and are reversible") {
    val image = BinaryPixelImage.blank(2, 3)
    val position = PixelPosition(1, 1)
    assertEquals(image.index(position), 4)
    val toggled = image.toggle(position)
    assertEquals(toggled.pixels, List(false, false, false, false, true, false))
    assertEquals(image.pixels, List.fill(6)(false))
    assertEquals(toggled.toggle(position), image)
    assert(toggled.at(position))
  }
  test("binary rows preserve rectangular non-square images and zeros") {
    val rows = List("010", "100")
    val image = BinaryPixelImage.fromRows(rows)
    assertEquals(image.rows, 2); assertEquals(image.columns, 3)
    assertEquals(image.binaryRows, rows)
    assertEquals(BinaryPixelImage.blank(1, 1).binaryRows, List("0"))
  }
  test("invalid dimensions, pixel counts and binary text fail early") {
    for ((rows, cols) <- List((0, 3), (2, 0), (-1, 3), (33, 1), (1, 33), (Int.MaxValue, Int.MaxValue)))
      intercept[IllegalArgumentException](BinaryPixelImage.blank(rows, cols))
    intercept[IllegalArgumentException](BinaryPixelImage(2, 3, List(false)))
    for (rows <- List(Nil, List(""), List("01", "0"), List("02"), List(" 1")))
      intercept[IllegalArgumentException](BinaryPixelImage.fromRows(rows))
    intercept[IllegalArgumentException](PixelPosition(-1, 0))
    intercept[IllegalArgumentException](seven.at(PixelPosition(5, 0)))
    intercept[IllegalArgumentException](seven.toggle(PixelPosition(0, 3)))
  }
  test("pixel matching includes inactive pixels and rejects different image shapes") {
    assertEquals(seven.matchingPixels(seven), 15)
    assertEquals(seven.matchingPixels(BinaryPixelImage.blank(5, 3)), 8)
    assertEquals(seven.matchingPixels(seven.toggle(PixelPosition(4, 0))), 14)
    intercept[IllegalArgumentException](seven.matchingPixels(BinaryPixelImage.blank(3, 5)))
  }
  test("unit-weight probes activate at the exact threshold and ignore unrelated pixels") {
    assertEquals(top.activeCount(seven), 3); assert(top.activates(seven))
    assert(!top.activates(seven.toggle(PixelPosition(0, 1))))
    assert(top.activates(seven.toggle(PixelPosition(4, 0))))
    assert(top.copy(threshold = 0).activates(BinaryPixelImage.blank(5, 3)))
    assert(top.copy(threshold = 2).activates(seven.toggle(PixelPosition(0, 1))))
  }
  test("invalid probe coordinates, duplicate pixels and thresholds are rejected") {
    intercept[IllegalArgumentException](top.copy(cells = Nil))
    intercept[IllegalArgumentException](top.copy(cells = List(PixelPosition(0, 0), PixelPosition(0, 0))))
    intercept[IllegalArgumentException](top.copy(threshold = -1))
    intercept[IllegalArgumentException](top.copy(threshold = 4))
    intercept[IllegalArgumentException](exercise.copy(probes = List(top.copy(cells = List(PixelPosition(5, 0)), threshold = 1))))
  }
  test("target grading accepts exactly the target and reports partial progress") {
    assertEquals(exercise.isCorrect(exercise.defaultValue), Some(false))
    assertEquals(exercise.matchingPixels(exercise.defaultValue), Some(8))
    assertEquals(exercise.isCorrect(seven), Some(true))
    assertEquals(exercise.matchingPixels(seven.toggle(PixelPosition(1, 0))), Some(14))
    assertEquals(exercise.outputs(seven), List(true))
    val ungraded = exercise.copy(expected = None, probes = Nil)
    assertEquals(ungraded.isCorrect(seven), None)
    assertEquals(ungraded.matchingPixels(seven), None)
    assertEquals(ungraded.outputs(seven), Nil)
  }
  test("targets and presets must have the same shape as the canvas") {
    val wrong = BinaryPixelImage.blank(3, 5)
    intercept[IllegalArgumentException](exercise.copy(expected = Some(wrong)))
    intercept[IllegalArgumentException](exercise.copy(presets = List(PixelPreset(id("wrong"), wrong))))
  }
  test("all model operations and the persistence codec reject mismatched saved dimensions") {
    val wrong = BinaryPixelImage.blank(3, 5)
    val ungraded = exercise.copy(expected = None, probes = Nil)
    for (e <- List(exercise, ungraded)) {
      intercept[IllegalArgumentException](e.toggle(wrong, PixelPosition(0, 0)))
      intercept[IllegalArgumentException](e.isCorrect(wrong))
      intercept[IllegalArgumentException](e.matchingPixels(wrong))
      intercept[IllegalArgumentException](e.outputs(wrong))
      intercept[SerializedException](e.serializerInteractionContent.serialize(wrong))
      intercept[SerializedException](e.serializerInteractionContent.deserialize(write(wrong)))
    }
  }
  test("both definition formats preserve targets, probes and source presets") {
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(exercise)), exercise)
  }
  test("saved binary images round-trip independently from target and preset content") {
    val edited = exercise.toggle(seven, PixelPosition(1, 0))
    assertEquals(exercise.serializerInteractionContent.deserialize(exercise.serializerInteractionContent.serialize(edited)), edited)
    assertEquals(exercise.defaultValue, BinaryPixelImage.blank(5, 3))
    assertEquals(exercise.expected, Some(seven))
    assertEquals(exercise.presets.head.image, seven)
  }
}
