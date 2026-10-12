package it.evadid.core.datastructures.color

import munit.FunSuite
import upickle.default.*

class ColorPackageSpec extends FunSuite {
  test("RGB hex encoding pads components and parses them back") {
    val color = RGBColor(1, 15, 254)
    assertEquals(color.toHex(), "#010ffe")
    assertEquals(RGBColor.fromPureSixDigitHex("010ffe"), color)
    assertEquals(color.toRGB, color)
    assertEquals(color.toWebColor, WebColorHexString("010ffe"))
  }
  test("non-primary RGB colors preserve hue through HSB round trips") {
    List(RGBColor(255, 128, 0), RGBColor(60, 180, 120), RGBColor(120, 60, 180), RGBColor.black, RGBColor.white).foreach { color =>
      assertEquals(color.toHSB.toRGB, color)
    }
  }
  test("grayscale HSB colors are opaque and hue wraps around") {
    assertEquals(HSBColor(0.5, 0, 1).toRGB, RGBColor.white)
    assertEquals(HSBColor(0, 0, 0).toRGB, RGBColor.black)
    assertEquals(HSBColor(1, 1, 1).toRGB, RGBColor.red)
    assertEquals(HSBColor(-1, 1, 1).toRGB, RGBColor.red)
    intercept[AssertionError](HSBColor(0, 2, 1).toRGB)
  }
  test("color gradients clamp endpoints and interpolate intermediate values") {
    val start = HSBColor(0, 1, 1)
    val end = HSBColor(0.5, 1, 1)
    assertEquals(RGBColor.getColorGradientHSB(start, end, -1).toRGB, start.toRGB)
    assertEquals(RGBColor.getColorGradientHSB(start, end, 2).toRGB, end.toRGB)
    assertEquals(RGBColor.getColorGradientHSB(start, end, 0.5).toRGB, HSBColor(0.25, 1, 1).toRGB)
  }
  test("RGB and HSB values have default codecs") {
    val rgb = RGBColor(1, 15, 254, 123)
    val hsb = HSBColor(0.25, 0.8, 0.6)
    assertEquals(read[RGBColor](write(rgb)), rgb)
    assertEquals(read[HSBColor](write(hsb)), hsb)
  }
}
