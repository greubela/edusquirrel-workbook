package it.evadid.core.datastructures.color

import munit.FunSuite

class ColorEdgeCaseUnitTest extends FunSuite {
  test("rgb parser rejects out of range and accepts integer style") {
    assert(WebColor.parseWebStyleRgbString("rgb(256,0,0)").isEmpty)
    assertEquals(WebColor.parseWebStyleRgbString("rgb(255, 1, 0)").get, RGBColor(255, 1, 0, 255))
  }

  test("gradient clamps percent") {
    val start = RGBColor(0, 0, 0)
    val dest = RGBColor(255, 255, 255)
    val low = RGBColor.getColorGradientRGB(start, dest, -1.0)
    val high = RGBColor.getColorGradientRGB(start, dest, 2.0)
    assert(low.red >= 0 && low.red <= 255)
    assert(high.red >= 0 && high.red <= 255)
  }
}
