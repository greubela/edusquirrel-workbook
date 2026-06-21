package it.evadid.evacuation.js

import it.evadid.evacuation.core.graphic.model.EvaColor
import javafx.scene.paint.Color

object ExampleJvmClass {


  def main(args: Array[String]): Unit = {

    println("Color 1 hsb: " + getHSBString(Color.RED) + ", Color 2 hsb: " + getHSBString(Color.GREEN))
    println("Color 1 hsb (eva): " + EvaColor(255, 0, 0).toHSB + ", Color 2 hsb (eva): " + EvaColor(0, 255, 0).toHSB)
    println()

    println(EvaColor.RGBtoHSB(Color.GREEN.getRed, Color.GREEN.getGreen, +Color.GREEN.getBlue))

    val res = getColorGradientFx(Color.RED, Color.GREEN, 10, false)


    println("gradiant: (" + res.getRed + ", " + res.getGreen + ", " + res.getBlue + ")")

  }

  def getHSBString(col: Color): String = {
    "(" + col.getHue + ", " + col.getSaturation + ", " + col.getBrightness + ")"
  }

  def hai(): Unit = {
    println("KTHXBYE!")
  }


  @Deprecated
  def getColorGradientFx(startColor: Color, destColor: Color, percent: Double, incHue: Boolean = true): Color = {
    val savePercent = ensureRange(percent, 0, 1)
    val destHue =
      if (incHue && destColor.getHue < startColor.getHue) destColor.getHue + 360
      else if (!incHue && destColor.getHue > startColor.getHue) destColor.getHue - 360
      else destColor.getHue

    val hueDiff: Double = destHue - startColor.getHue

    val curHue: Float = (((startColor.getHue + savePercent * hueDiff) + 5 * 360) % 360).asInstanceOf[Float]

    val brightDiff = destColor.getBrightness - startColor.getBrightness
    val satDiff = destColor.getSaturation - startColor.getSaturation

    val curSat = startColor.getSaturation + savePercent * satDiff
    val curBri = startColor.getBrightness + savePercent * brightDiff

    println("### gradiant input: percent: " + percent + ", incHue: " + incHue + ", destHue: " + destHue)
    println("### gradiant calc : hueDiff: " + hueDiff + ", brightDiff: " + brightDiff + ", satDiff: " + satDiff)
    println("### gradiant res  : curHue: " + curHue + ", curSat: " + curSat + ", curBri: " + curBri)

    Color.hsb(curHue, curSat, curBri)
  }


  def ensureRange(cur: Double, min: Double, max: Double): Double =
    if (cur < min) min else if (cur > max) max else cur

}
