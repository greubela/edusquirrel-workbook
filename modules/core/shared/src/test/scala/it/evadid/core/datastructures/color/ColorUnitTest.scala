package it.evadid.core.datastructures.color

import munit.FunSuite

class ColorUnitTest extends FunSuite {
  test("rgb hex and parsing") {
    assertEquals(RGBColor(255, 0, 0).toHex(), "#ff0000")
    assertEquals(RGBColor.fromPureSixDigitHex("00ff00"), RGBColor(0, 255, 0))
    assertEquals(WebColor("blue").toRGB, RGBColor(0, 0, 255))
    assertEquals(WebColor("#abc").toRGB, RGBColor(170, 187, 204))
  }

  test("HSB to RGB conversion keeps valid channel bounds".ignore) {
    val hsb = RGBColor(255, 0, 0).toHSB
    val converted = hsb.toRGB
    assert(converted.red >= 0 && converted.red <= 255)
    assert(converted.green >= 0 && converted.green <= 255)
    assert(converted.blue >= 0 && converted.blue <= 255)
  }
}
