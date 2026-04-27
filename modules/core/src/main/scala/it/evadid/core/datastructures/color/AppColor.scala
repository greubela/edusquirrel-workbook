package it.evadid.core.datastructures.color

import scala.annotation.tailrec

trait AppColor {
  def toRGB: RGBColor

  def toHSB: HSBColor

  def toWebColor: WebColor
}

object AppColor {



  /*
    def getStyle(): CSSStyleDeclaration = {
    val appDiv = document.getElementById("app")
    val style = window.getComputedStyle(appDiv)
    style
  }
  
*/
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
