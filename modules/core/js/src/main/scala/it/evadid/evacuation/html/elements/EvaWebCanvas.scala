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

    // Passes each listener the event position relative to the canvas.
    def relayPosition(event: dom.MouseEvent)(notify: (EvaMouseListener, Double, Double) => Unit): Unit =
      listener.foreach(mL => notify(mL, event.clientX - canvas.getBoundingClientRect().left, event.clientY - canvas.getBoundingClientRect().top))

    canvas.onclick = event => relayPosition(event)(_.onMouseClicked(_, _, event.button == 0))
    canvas.onmousemove = event => relayPosition(event)(_.onMouseMoved(_, _))
    canvas.onmouseenter = event => relayPosition(event)(_.onMouseEntered(_, _))
    canvas.onmouseleave = event => relayPosition(event)(_.onMouseExited(_, _))

    (canvas, ctx)
  }

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
    ctx.fillStyle = cssColor(color)
    color
  }

  override def setStrokeColor(color: EvaColor): EvaColor = {
    this.strokeColor = color
    ctx.strokeStyle = cssColor(color)
    color
  }

  private def cssColor(color: EvaColor): String =
    "rgb(" + color.red + ", " + color.green + ", " + color.blue + ", " + (color.alpha / 255.0) + ")"

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
    ctx.globalAlpha = if (alpha >= 0 && alpha <= 1.0) alpha else 1.0
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

  override def addMouseListener(mL: EvaMouseListener): Unit = {
    listener += mL
  }

  override def removeMouseListener(mL: EvaMouseListener): Unit = {
    listener -= mL
  }

  override def drawCircle(x: Double, y: Double, diameter: Double, strokeWidth: Double = 1): Unit = {
    ctx.beginPath()
    ctx.arc(x, y, diameter / 2.0, 0, 2 * Math.PI)
    strokeWithWidth(strokeWidth)
  }

  override def fillCircle(x: Double, y: Double, diameter: Double): Unit = {
    ctx.beginPath()
    ctx.arc(x, y, diameter / 2.0, 0, 2 * Math.PI)
    ctx.fill()
  }

  override def drawImageWithAlpha(x: Double, y: Double, alphaUpTp255: Double, img: EvaImage): Unit =
    ImageDrawer.drawImage(this, img, x, y, -1, -1, alphaUpTp255 / 255.0)

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
    arcPath(x, y, diameter, startAngle, arcAngle)
    strokeWithWidth(strokeWidth)
  }


  /** Unlike [[drawArc]], (x, y) is the top-left corner of the circle's bounding box. */
  override def fillArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double): Unit = {
    ctx.beginPath()
    arcPath(x + diameter / 2, y + diameter / 2, diameter, startAngle, arcAngle)
    ctx.fill()
  }

  /** Adds an arc around (centerX, centerY); angles in degrees with 0 = top, positive arc angles run clockwise. */
  private def arcPath(centerX: Double, centerY: Double, diameter: Double, startAngle: Double, arcAngle: Double): Unit = {
    val startAnglePosJS = (startAngle - 90 + 720) % 360
    val endAnglePosJS = (startAnglePosJS + arcAngle + 720) % 360
    ctx.arc(centerX, centerY, diameter / 2.0, startAnglePosJS * Math.PI / 180.0, endAnglePosJS * Math.PI / 180.0, !(arcAngle > 0))
  }

  /** Strokes the current path with `strokeWidth` (negative = current width) and restores the previous width. */
  private def strokeWithWidth(strokeWidth: Double): Unit = {
    val oldStroke = ctx.lineWidth
    if (strokeWidth >= 0) {
      ctx.lineWidth = strokeWidth
    }
    ctx.stroke()
    ctx.lineWidth = oldStroke
  }
}