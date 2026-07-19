package it.evadid.core.datastructures.font

import it.evadid.core.datastructures.geometry.Dimension

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform

private[font] object PlatformFontMeasurer extends FontMeasurer {
  private val renderingContext = new FontRenderContext(new AffineTransform(), true, true)

  override def measureText(font: AppFont, text: String): Dimension[Double] = {
    val style =
      (if (font.bold) Font.BOLD else Font.PLAIN) |
        (if (font.italic) Font.ITALIC else Font.PLAIN)
    val awtFont = new Font(font.name, style, math.max(1, font.sizeInPx.round.toInt))
      .deriveFont(font.sizeInPx.toFloat)
    val bounds = awtFont.getStringBounds(text, renderingContext)

    Dimension(bounds.getWidth, bounds.getHeight)
  }
}
