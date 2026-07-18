package it.evadid.evacuation.core.graphic.model

case class EvaColor(red: Int, green: Int, blue: Int, alpha: Int = 255) {


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
    var h: Double = if (s == 0.0) 0.0
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


object EvaColor {

  val red: EvaColor = new EvaColor(255, 0, 0)
  val green: EvaColor = new EvaColor(0, 128, 0)
  val blue: EvaColor = new EvaColor(0, 0, 255)
  val white: EvaColor = new EvaColor(255, 255, 255)
  val black: EvaColor = new EvaColor(0, 0, 0);

  val darkGreen: EvaColor = new EvaColor(0, 100, 0)

  def getColorGradient2(startColor: HSBColor, destColor: HSBColor, percent: Double, incHue: Boolean = true): HSBColor = {

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
    //EvaColor(converted.getRed, converted.getGreen, converted.getBlue, converted.getAlpha)
  }

  def getColorGradient(startColor: EvaColor, destColor: EvaColor, percent: Double, incHue: Boolean = true): EvaColor = {
    // val savePercent = ensureRange(percent, 0, 1)
    val res = getColorGradient2(startColor.toHSB, destColor.toHSB, percent, incHue)
    res.toRGB
  }

  def RGBtoHSB(r: Double, g: Double, b: Double): Array[Double] = {
    var hue = .0
    var saturation = .0
    var brightness = .0
    val hsbvals = new Array[Double](3)
    var cmax = if (r > g) r
    else g
    if (b > cmax) cmax = b
    var cmin = if (r < g) r
    else g
    if (b < cmin) cmin = b
    brightness = cmax

    print("bbbbb: " + brightness + " ( " + r + ", " + g + ", " + b + ")")

    if (cmax != 0) saturation = (cmax - cmin).toDouble / cmax
    else saturation = 0
    if (saturation == 0) hue = 0
    else {
      val redc = (cmax - r) / (cmax - cmin)
      val greenc = (cmax - g) / (cmax - cmin)
      val bluec = (cmax - b) / (cmax - cmin)
      if (r == cmax) hue = bluec - greenc
      else if (g == cmax) hue = 2.0 + redc - bluec
      else hue = 4.0 + greenc - redc
      hue = hue / 6.0
      if (hue < 0) hue = hue + 1.0
    }
    hsbvals(0) = hue * 360
    hsbvals(1) = saturation
    hsbvals(2) = brightness
    hsbvals
  }

}
