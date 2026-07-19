package it.evadid.core.datastructures.font

import it.evadid.core.datastructures.geometry.Dimension

/** A platform-independent description of a font used by the application. */
final case class AppFont(
    name: String,
    sizeInPx: Double,
    italic: Boolean = false,
    bold: Boolean = false,
    variant: String = "normal"
) {
  def toCssString: String = {
    val style = if (italic) "italic" else "normal"
    val weight = if (bold) 700 else 400
    s"$style $variant $weight ${sizeInPx}px '$name'"
  }

  /** Measures text using the native font implementation of the current platform. */
  def measureText(text: String): Dimension[Double] =
    PlatformFontMeasurer.measureText(this, text)
}

object AppFont {
  def defaultFont: AppFont = AppFont("Arial", 12)
  def AnonymousPro: AppFont = AppFont("Anonymous Pro", 32)
  val Aptos: AppFont = AppFont("Aptos", 12)
}

/** Contract implemented by each core platform's native text measuring backend. */
trait FontMeasurer {
  def measureText(font: AppFont, text: String): Dimension[Double]
}
