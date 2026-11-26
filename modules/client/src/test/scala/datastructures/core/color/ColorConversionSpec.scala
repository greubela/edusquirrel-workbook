package datastructures.core.color

import it.evadid.core.datastructures.color.{HSBColor, RGBColor, WebColor}

import munit.FunSuite

class ColorConversionSpec extends FunSuite {

  test("WebColor.apply parses named, hex, and rgb formats with canonical formatting") {
    // input, parsed RGB, canonical hex, canonical rgb string
    List(
      ("Blue", RGBColor(0, 0, 255), "#0000ff", "rgb(0, 0, 255, 1)"),
      ("#0f8", RGBColor(0, 255, 136), "#00ff88", "rgb(0, 255, 136, 1)"),
      ("ff8800", RGBColor(255, 136, 0), "#ff8800", "rgb(255, 136, 0, 1)")
    ).foreach { (input, rgb, hex, rgbText) =>
      val color = WebColor(input)
      assertEquals(color.toRGB, rgb, clue(input))
      assertEquals(color.toRGB.toHex(), hex, clue(input))
      assertEquals(color.toWebColor.webStyleHexString, hex, clue(input))
      assertEquals(color.toWebColor.webStyleRgbString, rgbText, clue(input))
    }
    assertEquals(WebColor("Blue").toWebColor.webStyleNameOption, Some("blue"))

    val rgbString = WebColor("rgb(255, 136, 0, 0.5)")
    assertEquals(rgbString, RGBColor(255, 136, 0, 128))
    assertEquals(rgbString.toRGB.toHex(), "#ff8800")
    assertEquals(rgbString.toWebColor.webStyleHexString, "#ff8800")
    assertEquals(rgbString.toWebColor.webStyleRgbString, "rgb(255, 136, 0, 1)")
  }

  test("RGBColor toHSB/toRGB round-trips representative colors") {
    val expectedRoundTrips = Seq(
      RGBColor.black -> RGBColor.black,
      RGBColor.white -> RGBColor.white,
      RGBColor.red -> RGBColor.red,
      RGBColor.green -> RGBColor.green,
      RGBColor.blue -> RGBColor.blue,
      RGBColor(1, 0, 0) -> RGBColor(1, 0, 0),
      RGBColor(0, 0, 1) -> RGBColor(0, 0, 1)
    )

    expectedRoundTrips.foreach { case (source, expected) =>
      val roundTrip = source.toHSB.toRGB
      assertEquals(roundTrip, expected, clues(s"failed round-trip for $source"))
      assertEquals(roundTrip.toHex(), source.toHex())
      assertEquals(roundTrip.toWebColor.webStyleHexString, source.toWebColor.webStyleHexString)
      assertEquals(roundTrip.toWebColor.webStyleRgbString, expected.toWebColor.webStyleRgbString)
    }
  }

  test("RGBColor gradients clamp percent at boundaries and interpolate midpoint") {
    val start = RGBColor.black
    val end = RGBColor.white

    val belowZero = RGBColor.getColorGradientRGB(start, end, -0.1)
    val aboveOne = RGBColor.getColorGradientRGB(start, end, 1.5)
    val midpoint = RGBColor.getColorGradientRGB(start, end, 0.5)

    assertEquals(belowZero, RGBColor.black)
    assertEquals(aboveOne, RGBColor.white)
    assertEquals(midpoint, RGBColor(128, 128, 128))
    assertEquals(midpoint.toHex(), "#808080")
    assertEquals(midpoint.toWebColor.webStyleRgbString, "rgb(128, 128, 128, 1)")

    val hsbStart = HSBColor(0.0, 1.0, 1.0)
    val hsbEnd = HSBColor(1.0 / 3.0, 1.0, 1.0)

    val hsbBelowZero = RGBColor.getColorGradientHSB(hsbStart, hsbEnd, -0.2)
    val hsbAboveOne = RGBColor.getColorGradientHSB(hsbStart, hsbEnd, 1.2)
    val hsbMidpoint = RGBColor.getColorGradientHSB(hsbStart, hsbEnd, 0.5)

    assertEquals(hsbBelowZero.toRGB, hsbStart.toRGB)
    assertEquals(hsbAboveOne.toRGB, hsbEnd.toRGB)
    assertEquals(hsbMidpoint.toRGB, RGBColor(255, 255, 0))
    assertEquals(hsbMidpoint.toRGB.toHex(), "#ffff00")
    assertEquals(hsbMidpoint.toRGB.toWebColor.webStyleHexString, "#ffff00")
  }

  test("WebColor.parseWebStyleRgbString accepts valid forms and rejects invalid ranges") {
    assertEquals(WebColor.parseWebStyleRgbString("rgb(1, 0.5, 0, 0.25)"), Some(RGBColor(255, 128, 0, 64)))
    assertEquals(WebColor.parseWebStyleRgbString("rgb(255, 128, 0, 64)"), Some(RGBColor(255, 128, 0, 64)))
    assertEquals(WebColor.parseWebStyleRgbString("rgb(10, 20, 30)"), Some(RGBColor(10, 20, 30, 255)))

    assertEquals(WebColor.parseWebStyleRgbString("rgb(1.1, 0.5, 0)"), Some(RGBColor(1, 1, 0, 255)))

    assertEquals(WebColor.parseWebStyleRgbString("rgb(-1, 0, 0)"), None)
    assertEquals(WebColor.parseWebStyleRgbString("rgb(256, 0, 0)"), None)
    assertEquals(WebColor.parseWebStyleRgbString("rgb(0, 0, 0, 256)"), None)
    assertEquals(WebColor.parseWebStyleRgbString("rgba(0, 0, 0, 1)"), None)
    assertEquals(WebColor.parseWebStyleRgbString("rgb(0, 0)"), None)
  }
}
