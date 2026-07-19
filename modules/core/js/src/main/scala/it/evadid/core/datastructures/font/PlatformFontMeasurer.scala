package it.evadid.core.datastructures.font

import it.evadid.core.datastructures.geometry.Dimension
import org.scalajs.dom
import org.scalajs.dom.document

import scala.scalajs.js

private[font] object PlatformFontMeasurer extends FontMeasurer {
  override def measureText(font: AppFont, text: String): Dimension[Double] = {
    val canvas = document.createElement("canvas").asInstanceOf[dom.html.Canvas]
    val context = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    context.font = font.toCssString

    val metrics = context.measureText(text)
    val ascent = metrics.asInstanceOf[js.Dynamic].actualBoundingBoxAscent
    val descent = metrics.asInstanceOf[js.Dynamic].actualBoundingBoxDescent
    val height =
      if (js.isUndefined(ascent) || js.isUndefined(descent)) 0.0
      else ascent.asInstanceOf[Double] + descent.asInstanceOf[Double]

    Dimension(metrics.width, height)
  }
}
