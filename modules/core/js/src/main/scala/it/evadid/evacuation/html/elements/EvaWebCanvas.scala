package it.evadid.evacuation.html.elements

import it.evadid.evacuation.core.graphic.model.{EvaColor, EvaFont, EvaImage}
import it.evadid.evacuation.shared.traits.graphic.{EvaCanvas, EvaMouseListener}
import org.scalajs.dom
import org.scalajs.dom.html.{Canvas, Image}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class EvaWebCanvas(width: Double, height: Double) extends EvaCanvas[Canvas] {

  private val listener: mutable.ListBuffer[EvaMouseListener] = new ListBuffer()

  val (canvas, ctx) = initCanvas()

  private def initCanvas(): (Canvas, dom.CanvasRenderingContext2D) = {
    val canvas = dom.document.createElement("canvas").asInstanceOf[Canvas]
    canvas.width = width.asInstanceOf[Int]
    canvas.height = height.asInstanceOf[Int]
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

    canvas.onclick = event => listener.foreach(mL => mL.onMouseClicked(event.clientX - canvas.getBoundingClientRect().left, event.clientY - canvas.getBoundingClientRect().top, event.button == 0))
    canvas.onmousemove = event => listener.foreach(mL => mL.onMouseMoved(event.clientX - canvas.getBoundingClientRect().left, event.clientY - canvas.getBoundingClientRect().top))
    canvas.onmouseenter = event => listener.foreach(mL => mL.onMouseEntered(event.clientX - canvas.getBoundingClientRect().left, event.clientY - canvas.getBoundingClientRect().top))
    canvas.onmouseleave = event => listener.foreach(mL => mL.onMouseExited(event.clientX - canvas.getBoundingClientRect().left, event.clientY - canvas.getBoundingClientRect().top))

    (canvas, ctx)
  }

  private var scale = 1.0

  protected var fillColor: EvaColor = setFillColor(EvaColor(0, 0, 0, 255))
  protected var strokeColor: EvaColor = setStrokeColor(EvaColor(0, 0, 0, 255))

  def getFillColor: EvaColor = fillColor

  def getStrokeColor: EvaColor = strokeColor

  clear()

  override def getCanvasElement: Canvas = {
    canvas
  }

  override def getDrawingWidth: Double = width

  override def getDrawingHeight: Double = height

  override def setFillColor(color: EvaColor): EvaColor = {
    this.fillColor = color
    ctx.fillStyle = "rgb(" + color.red + ", " + color.green + ", " + color.blue + ", " + (color.alpha / 255.0) + ")"
    color
  }

  override def setStrokeColor(color: EvaColor): EvaColor = {
    this.strokeColor = color
    ctx.strokeStyle = "rgb(" + color.red + ", " + color.green + ", " + color.blue + ", " + (color.alpha / 255.0) + ")"
    color
  }

  override def drawRect(x: Double, y: Double, width: Double, height: Double): Unit =
    ctx.strokeRect(x, y, width, height)

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


  def drawLoadedImage(img: Image, x: Double, y: Double, width: Double, height: Double, alpha: Double = 1.0): Unit = {

    //println("drawLoadedImage(" + img + ", " + x + ", " + y + ", " + width + ", " + height + ", " + alpha + ")")

    if (alpha >= 0 && alpha <= 1.0) ctx.globalAlpha = alpha
    else ctx.globalAlpha = 1.0
    if (width > 0 && height > 0) {
      ctx.drawImage(img, x, y, width, height)
    } else {
      ctx.drawImage(img, x, y)
    }
    ctx.globalAlpha = 1.0
  }

  override def drawImage(x: Double, y: Double, img: EvaImage): Unit = {
    ImageDrawer.drawImage(this, img, x, y, -1, -1, 1.0)
  }


  override def drawStringCentered(x: Double, y: Double, content: String): Unit = {
    ctx.textAlign = "center"
    ctx.fillText(content, x, y)
  }

  /*
    override def getScalingFactor(): Double = scale

    override def setScalingFactor(scalingFactor: Double): Unit = {
      this.scale = scale
      ctx.scale(scalingFactor, scalingFactor)
    }
  */

  override def addMouseListener(mL: EvaMouseListener): Unit = {
    listener += mL
  }

  override def removeMouseListener(mL: EvaMouseListener): Unit = {
    listener -= mL
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

  override def drawImageWithAlpha(x: Double, y: Double, alphaUpTp255: Double, img: EvaImage): Unit = {

    ImageDrawer.drawImage(this, img, x, y, -1, -1, alphaUpTp255 / 255.0)


  }

  override def drawScaledImage(x: Double, y: Double, width: Double, height: Double, img: EvaImage): Unit = {
    ImageDrawer.drawImage(this, img, x, y, width, height, 1.0)
  }

  override def getTextWidth(str: String, font: EvaFont = null): Double = {
    val savedFont = ctx.font
    if (font != null) {
      setFont(font)
    }

    val dim = ctx.measureText(str)

    ctx.font = savedFont
    dim.width
  }

  override def setFont(font: EvaFont): Unit = {
    ctx.font = font.toCSSString
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

    val startAnglePosJS = (startAngle - 90 + 720) % 360
    val endAnglePosJS = (startAnglePosJS + arcAngle + 720) % 360

    val startAngleRad = startAnglePosJS * Math.PI / 180.0
    val endAngleRad = endAnglePosJS * Math.PI / 180.0


    if (arcAngle > 0) {
      ctx.arc(x + diameter / 2, y + diameter / 2, diameter / 2.0, startAngleRad, endAngleRad)
    } else {
      ctx.arc(x + diameter / 2, y + diameter / 2, diameter / 2.0, startAngleRad, endAngleRad, true)
    }
    ctx.fill()

  }
}

object EvaWebCanvas {

  private case class DrawingInformation(x: Double, y: Double)


}