package contentmanagement.model.color

import contentmanagement.model.color.{HSBColor, RGBColor}

trait AppColor {
  def toRGB: RGBColor

  def toHSB: HSBColor

  def toWebStyleString: String = {
    val rgb = toRGB
    "rgb(" + rgb.red + ", " + rgb.green + ", " + rgb.blue + ", " + (rgb.alpha / 255.0) + ")"
  }
}

object AppColor {

  /*
    def getStyle(): CSSStyleDeclaration = {
    val appDiv = document.getElementById("app")
    val style = window.getComputedStyle(appDiv)
    style
  }
  
  def readColorFromCSS(cssName: String): RGBColor = {
    val style = ViewElements.getStyle()
    val hexCol = style.getPropertyValue(cssName)
    assert(hexCol.length == 7, "hex color '" + hexCol + "' should have exactly one '#' and six hex characters!")
    val pureHex = hexCol.substring(1, 7) // remove "#" at start and other things
    val r = Integer.parseInt(pureHex.substring(0, 2), 16)
    val g = Integer.parseInt(pureHex.substring(2, 4), 16)
    val b = Integer.parseInt(pureHex.substring(4, 6), 16)
    RGBColor(r, g, b)
  }*/
  /*
  lazy val cssBackgroundDark: RGBColor = readColorFromCSS("--color-background-dark")
  lazy val cssBackgroundDarkLighter: RGBColor = readColorFromCSS("--color-background-dark-lighter")
  lazy val cssBackgroundLightInput: RGBColor = readColorFromCSS("--color-input-light")
  
    lazy val cssBackgroundDark: RGBColor = RGBColor.red
    lazy val cssBackgroundDarkLighter: RGBColor = RGBColor.yellow
    lazy val cssBackgroundLightInput: RGBColor = RGBColor.green
  
  lazy val cssContrastColorMain: RGBColor = readColorFromCSS("--color-main-contrast")
  lazy val cssContrastColorMainLighter: RGBColor = readColorFromCSS("--color-main-contrast-lighter")*/
}
