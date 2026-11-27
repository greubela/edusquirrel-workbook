package it.evadid.core.datastructures.color

import munit.FunSuite

class ColorEdgeCaseUnitTest extends FunSuite {
  test("rgb parser rejects out of range and accepts integer style") {
    assert(WebColor.parseWebStyleRgbString("rgb(256,0,0)").isEmpty)
    assertEquals(WebColor.parseWebStyleRgbString("rgb(255, 1, 0)").get, RGBColor(255, 1, 0, 255))
  }
}
