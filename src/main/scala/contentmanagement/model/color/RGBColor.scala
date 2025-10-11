package contentmanagement.model.color

case class RGBColor(red: Int, green: Int, blue: Int, alpha: Int = 255) extends AppColor {

  def toRGB: RGBColor = this

  def toHex(prefix: String = "#"): String =
    prefix + (red / 16).toHexString + (red % 16).toHexString + (green / 16).toHexString + (green % 16).toHexString + (blue / 16).toHexString + (blue % 16).toHexString


  def toHSB: HSBColor = {
    // SOURCE: https://developer.classpath.org/doc/java/awt/Color-source.html

    // BRIGTHNESS
    var min: Int = -1
    var max: Int = -1
    if (red < green) {
      min = red;
      max = green;
    }
    else {
      min = green;
      max = red;
    }
    if (blue > max) max = blue;
    else if (blue < min) min = blue;

    val b = max / 255.0;
    //SATURATION

    val s =
      if (max == 0) 0.0
      else ((max - min) * 1.0) / (max * 1.0)

    // HUE
    var h: Double = if (s == 0.0) 0.0;
    else {
      val delta = (max - min) * 6;
      if (red == max) (green - blue) / delta;
      else if (green == max) 1.0 / 3 + (blue - red) / delta;
      else 2.0 / 3 + (red - green) / delta;
    }
    if (h < 0) h = h + 1

    HSBColor(h, s, b)
  }

}


object RGBColor {

  val red: RGBColor = new RGBColor(255, 0, 0)
  val green: RGBColor = new RGBColor(0, 128, 0)
  val blue: RGBColor = new RGBColor(0, 0, 255)
  val white: RGBColor = new RGBColor(255, 255, 255)
  val black: RGBColor = new RGBColor(0, 0, 0);
  val yellow: RGBColor = new RGBColor(255, 255, 0)

  val darkGreen: RGBColor = new RGBColor(0, 50, 0)
  val transparent: RGBColor = new RGBColor(0, 0, 0, 0)

  def getColorGradientHSB(startColor: HSBColor, destColor: HSBColor, percent: Double, incHue: Boolean = true): HSBColor = {

    val savePercent = if (percent < 0) 0 else if (percent > 1) 1 else percent

    val destHue =
      if (incHue && destColor.hue < startColor.hue) destColor.hue + 1.0
      else if (!incHue && destColor.hue > startColor.hue) destColor.hue - 1.0
      else destColor.hue

    val hueDiff: Double = destHue - startColor.hue
    val brightDiff = destColor.brightness - startColor.brightness
    val satDiff = destColor.saturation - startColor.saturation

    val curHue: Float = (((startColor.hue + savePercent * hueDiff) + 5) % 1).asInstanceOf[Float]
    val curSat: Float = (startColor.saturation + savePercent * satDiff).asInstanceOf[Float]
    val curBri: Float = (startColor.brightness + savePercent * brightDiff).asInstanceOf[Float]

    HSBColor(curHue, curSat, curBri)
    //val converted = java.awt.Color.getHSBColor(curHue, curSat, curBri)
    //RGBColor(converted.getRed, converted.getGreen, converted.getBlue, converted.getAlpha)
  }

  def getColorGradientRGB(startColor: RGBColor, destColor: RGBColor, percent: Double, incHue: Boolean = true): RGBColor = {
    // val savePercent = ensureRange(percent, 0, 1)
    val res = getColorGradientHSB(startColor.toHSB, destColor.toHSB, percent, incHue)
    res.toRGB
  }


}

