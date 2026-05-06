package it.evadid.core.datastructures.color

import munit.FunSuite

class ColorIntegrationTest extends FunSuite {
  test("palette structure and rgb parser") {
    val palette = AppColorPalette.defaultRGBYPalette25
    assertEquals(palette.allColors.size, 25)
    assertEquals(palette.reds.size, 5)

    val parsed = WebColor.parseWebStyleRgbString("rgb(0.5, 0.5, 1, 0.5)")
    assertEquals(parsed.get, RGBColor(128, 128, 255, 128))
  }
}
