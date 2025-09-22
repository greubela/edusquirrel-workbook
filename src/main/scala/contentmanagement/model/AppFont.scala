package contentmanagement.model

case class AppFont(name: String, size: Double, data: Array[Byte]) {

  def toCSSString: String = size + "px " + name

  /*
  def getAwtTextDimension(content: String): (Double, Double) = {
    //val res = Graphics.getFontMetrics()
    val font: Font = new Font(name, Font.PLAIN, size.toInt)
    val ctx = new FontRenderContext(new AffineTransform(), true, true)
    val bounds = font.getStringBounds(content, ctx)

    (bounds.getWidth, bounds.getHeight)
  }*/


  /*

  def getTextWidth(str: String, font: AppFont = null): Double = {
    val savedFont = ctx.font
    if (font != null) {
      setFont(font)
    }

    val dim = ctx.measureText(str)

    ctx.font = savedFont
    dim.width
  }
   */

}

object AppFont {

  def getBasicFont: AppFont = courierNew

  val wizardsFont: AppFont = AppFont("Beleren Small Caps", 14, Array.empty)

  val fira = AppFont("FiraSans", 20, Array.empty)

  val courierNew: AppFont = AppFont("Courier New", 20, Array.empty)
  val aptos: AppFont = AppFont("Aptos", 12, Array.empty)

}