package contentmanagement.model

import contentmanagement.model.geometry.Dimension
import org.scalajs.dom
import org.scalajs.dom.document

import scala.scalajs.js

case class AppFont(
                    name: String,
                    sizeInPx: Double,
                    italic: Boolean = false,
                    bold: Boolean = false,
                    variant: String = "normal"
                  ) {
  def toCssString: String = {
    val style = if (italic) "italic" else "normal"
    val weight = if (bold) 700 else 400
    s"$style $variant $weight ${sizeInPx}px '${name}'"
  }

  def measureText(text: String): Dimension[Double] = {
    val canvas = document.createElement("canvas").asInstanceOf[dom.html.Canvas]
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    ctx.font = toCssString

    val m = ctx.measureText(text)
    val asc = m.asInstanceOf[js.Dynamic].actualBoundingBoxAscent
    val desc = m.asInstanceOf[js.Dynamic].actualBoundingBoxDescent

    val h: Double =
      if (js.isUndefined(asc) || js.isUndefined(desc)) 0.0
      else asc.asInstanceOf[Double] + desc.asInstanceOf[Double]

    new Dimension[Double](ctx.measureText(text).width, h)
  }

}


object AppFont {

  def defaultFont: AppFont = AnonymousPro

  def AnonymousPro: AppFont = AppFont("Anonymous Pro", 32)

  val Aptos: AppFont = AppFont("Aptos", 12)
  /*
  val wizardsFont: AppFont = AppFont("Beleren Small Caps", 14, Array.empty)

  val fira = AppFont("FiraSans", 20, Array.empty)

  val courierNew: AppFont = AppFont("Courier New", 20, Array.empty)*/

}