package it.evadid.core.datastructures.canvas

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.color.{AppColor, RGBColor}
import it.evadid.core.datastructures.file.*
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.Canvas
import it.evadid.core.datastructures.font.AppFont

import scala.scalajs.js.JSConverters.*
class WebCanvas(canvas: ReactiveHtmlElement[Canvas], width: Int, height: Int) extends AppCanvas[ReactiveHtmlElement[Canvas]] {

  def getDomElement(): HtmlElement = getCanvas

  val ctx: CanvasRenderingContext2D = canvas.ref.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  //def setAttributeResolution(setWidth: Int = width, setHeight: Int = height): Unit = {
  canvas.ref.width = width
  canvas.ref.height = height


  var fillColor: AppColor = setFillColor(RGBColor(255, 0, 0, 255))
  var strokeColor: AppColor = setStrokeColor(RGBColor(0, 0, 0, 255))

  clear()

  override def getFillColor: AppColor = fillColor

  override def getStrokeColor: AppColor = strokeColor

  override def getCanvas: ReactiveHtmlElement[Canvas] = canvas

  override def getWidth: Double = canvas.ref.width

  override def getHeight: Double = canvas.ref.height


  override def setFillColor(color: AppColor): AppColor = {
    fillColor = color
    ctx.fillStyle = color.toWebColor.webStyleRgbString
    color
  }

  override def setStrokeColor(color: AppColor): AppColor = {
    strokeColor = color
    ctx.strokeStyle = color.toWebColor.webStyleRgbString
    color
  }

  override def drawRect(x: Double, y: Double, width: Double, height: Double, strokeWidth: Double = 1): Unit = {
    if (strokeWidth >= 0) {
      ctx.lineWidth = strokeWidth
    }
    ctx.strokeRect(x, y, width, height)
  }

  override def fillRect(x: Double, y: Double, width: Double, height: Double): Unit =
    ctx.fillRect(x, y, width, height)

  override def drawLine(x1: Double, y1: Double, x2: Double, y2: Double, width: Double): Unit = {
    val oldWidth = ctx.lineWidth
    ctx.lineWidth = width

    ctx.beginPath()
    ctx.moveTo(x1, y1)
    ctx.lineTo(x2, y2)
    ctx.stroke()

    ctx.lineWidth = oldWidth
  }

  override def drawCubicBezier(
      startX: Double,
      startY: Double,
      control1X: Double,
      control1Y: Double,
      control2X: Double,
      control2Y: Double,
      endX: Double,
      endY: Double,
      strokeWidth: Double,
      dashPattern: Option[Seq[Double]]
  ): Unit = {
    val oldWidth = ctx.lineWidth
    val oldDash = ctx.getLineDash()
    ctx.lineWidth = strokeWidth
    dashPattern.filter(_.nonEmpty).foreach(pattern => ctx.setLineDash(pattern.map(_.toDouble).toJSArray))

    ctx.beginPath()
    ctx.moveTo(startX, startY)
    ctx.bezierCurveTo(control1X, control1Y, control2X, control2Y, endX, endY)
    ctx.stroke()

    ctx.lineWidth = oldWidth
    ctx.setLineDash(oldDash)
  }

  def drawLoadedImage(img: Image, x: Double, y: Double, width: Double, height: Double, alpha: Double = 1.0): Unit = {
    //println("drawLoadedImage(" + img + ", " + x + ", " + y + ", " + width + ", " + height + ", " + alpha + ")")

  }

  override def drawStringCentered(x: Double, y: Double, content: String): Unit = {
    ctx.textAlign = "center"
    ctx.textBaseline = "middle"
    ctx.fillText(content, x, y)
  }

  override def drawCircle(x: Double, y: Double, diameter: Double, strokeWidth: Double = 1): Unit = {
    val oldStroke = ctx.lineWidth
    if (strokeWidth >= 0) {
      ctx.lineWidth = strokeWidth
    }

    ctx.beginPath()
    ctx.arc(x, y, diameter / 2.0, 0, 2 * Math.PI)

    ctx.stroke()

    ctx.lineWidth = oldStroke
  }

  override def fillCircle(x: Double, y: Double, diameter: Double): Unit = {
    ctx.beginPath()
    ctx.arc(x, y, diameter / 2.0, 0, 2 * Math.PI)
    ctx.fill()
  }

  override def drawImage(x: Double, y: Double, width: Double, height: Double, image: AppImage, alphaUpTp255: Double = 255): Unit = {

    if (alphaUpTp255 >= 0 && alphaUpTp255 <= 255) ctx.globalAlpha = alphaUpTp255 / 255.0
    else ctx.globalAlpha = 1.0
    //if (width > 0 && height > 0) {
    val imageElement = dom.document.createElement("img").asInstanceOf[dom.html.Image]
    imageElement.src = image.imageSourceString
    ctx.drawImage(imageElement, x, y, width, height)
    //} else {
    //ctx.drawImage(img.domImage, x, y)
    //}
    ctx.globalAlpha = 1.0

  }

  override def setFont(font: AppFont): Unit = {
    ctx.font = font.toCssString
  }

  /**
   * Draws an Arc on the Canvas
   *
   * @param x           the x-Position of the circle-center
   * @param y           the y-Position of the circle-center
   * @param diameter    the diameter of the circle
   * @param startAngle  the start angle (0-360, 0 = top)
   * @param arcAngle    the arc angle (0-360, positive = clockwise)
   * @param strokeWidth the width of the stroke (-1 = default)
   */
  override def drawArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double, strokeWidth: Double = -1): Unit = {

    ctx.beginPath()

    val startAnglePosJS = (startAngle - 90 + 720) % 360
    val endAnglePosJS = (startAnglePosJS + arcAngle + 720) % 360

    val startAngleRad = startAnglePosJS * Math.PI / 180.0
    val endAngleRad = endAnglePosJS * Math.PI / 180.0

    if (arcAngle > 0) {
      ctx.arc(x, y, diameter / 2.0, startAngleRad, endAngleRad)
    } else {
      ctx.arc(x, y, diameter / 2.0, startAngleRad, endAngleRad, true)
    }


    val oldStroke = ctx.lineWidth
    if (strokeWidth >= 0) {
      ctx.lineWidth = strokeWidth
    }

    ctx.stroke()

    ctx.lineWidth = oldStroke
  }


  override def fillArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double): Unit = {

    ctx.beginPath()

    ctx.moveTo(x, y)

    val (startX, startY) = AppCanvas.calcArcPoint(x, y, diameter, startAngle)
    val (endX, endY) = AppCanvas.calcArcPoint(x, y, diameter, startAngle + arcAngle)


    ctx.lineTo(startX, startY)
    ctx.lineTo(endX, endY)

    val startAnglePosJS = (startAngle - 90 + 720) % 360
    val endAnglePosJS = (startAnglePosJS + arcAngle + 720) % 360

    val startAngleRad = startAnglePosJS * Math.PI / 180.0
    val endAngleRad = endAnglePosJS * Math.PI / 180.0


    if (arcAngle > 0) {
      ctx.arc(x, y, diameter / 2.0, startAngleRad, endAngleRad)
    } else {
      ctx.arc(x, y, diameter / 2.0, startAngleRad, endAngleRad, true)
    }

    ctx.fill()
  }

  def clear(clearColor: AppColor = RGBColor.white): Unit = {
    val oldColor = getFillColor
    setFillColor(clearColor)
    fillRect(0, 0, width, height)
    setFillColor(oldColor)
  }

}


object WebCanvas {

  private case class DrawingInformation(x: Double, y: Double)

  def apply(width: Int, height: Int): WebCanvas = {
    val myCanvas: ReactiveHtmlElement[Canvas] = canvasTag(
      // backgroundColor := "red", // optional styling,
      idAttr := "myCanvas",
      inContext { thisNode =>
        onMountCallback { _ =>
          //    val actualCanvas = thisNode.ref.asInstanceOf[dom.html.Canvas]
          //   resCanvas = WebCanvas(actualCanvas, width, height)
        }
      }
    )
    new WebCanvas(myCanvas, width, height)
  }


}
