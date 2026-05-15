package it.evadid.core.datastructures.color

case class HSBColor(hue: Double, saturation: Double, brightness: Double) extends AppColor {


  override def toWebColor: WebColor = toRGB.toWebColor
  
  def toRGB: RGBColor = {

    assert(saturation >= 0 && saturation <= 1 && brightness >= 0 && brightness <= 1, "checking bounding condition of HSB")

    if (saturation == 0) convert(brightness, brightness, brightness, 0)
    else {
      val newHue = hue - Math.floor(hue);

      val i = Math.floor(6 * newHue).toInt
      val f = 6 * newHue - i;

      val p = brightness * (1 - saturation);
      val q = brightness * (1 - saturation * f);
      val t = brightness * (1 - saturation * (1 - f));

      if (i == 0) convert(brightness, t, p )
      else if (i == 1) convert(q, brightness, p )
      else if (i == 2) convert(p, brightness, t)
      else if (i == 3) convert(p, q, brightness)
      else if (i == 4) convert(t, p, brightness )
      else if (i == 5) convert(brightness, p, q)
      else {
        println("WARNING: NOT POSSIBLE (HSBCOLOR30)")
        null
      }

    }

  }

  def toHSB: HSBColor = this

  private def convert(red: Double, green: Double, blue: Double, alpha: Double = 1.0): RGBColor = {
    assert(red >= 0 && red <= 1 && green >= 0 && green <= 1 && blue >= 0 && blue <= 1 && alpha >= 0 && alpha <= 1, "Bad RGB values")
    val redval = Math.round(255 * red).asInstanceOf[Int]
    val greenval = Math.round(255 * green).asInstanceOf[Int]
    val blueval = Math.round(255 * blue).asInstanceOf[Int]
    val alphaval = Math.round(255 * alpha).asInstanceOf[Int]
    RGBColor(redval, greenval, blueval, alphaval)

  }

}
