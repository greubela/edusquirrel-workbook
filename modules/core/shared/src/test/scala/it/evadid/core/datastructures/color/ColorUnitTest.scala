package it.evadid.core.datastructures.color

import munit.FunSuite

class ColorUnitTest extends FunSuite {
  test("rgb hex and parsing") {
    assertEquals(RGBColor(255, 0, 0).toHex(), "#ff0000")
    assertEquals(RGBColor.fromPureSixDigitHex("00ff00"), RGBColor(0, 255, 0))
    assertEquals(WebColor("blue").toRGB, RGBColor(0, 0, 255))
    assertEquals(WebColor("#abc").toRGB, RGBColor(170, 187, 204))
  }
}
