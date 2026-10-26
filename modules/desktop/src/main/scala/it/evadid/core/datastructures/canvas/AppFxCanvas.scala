package it.evadid.core.datastructures.canvas

import it.evadid.core.datastructures.color.{AppColor, RGBColor}
import it.evadid.core.datastructures.font.AppFont
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.image.Image
import javafx.scene.paint.Color
import javafx.scene.shape.ArcType
import javafx.scene.text.{Font, FontPosture, FontWeight, TextAlignment}
import javafx.geometry.VPos

/** JavaFX implementation of the application canvas contract. */
class AppFxCanvas(width: Double, height: Double) extends AppCanvas[Canvas] {
  private val canvas = new Canvas(width, height)
  private val context: GraphicsContext = canvas.getGraphicsContext2D
  private var fillColor: AppColor = RGBColor(0, 0, 0, 255)
  private var strokeColor: AppColor = RGBColor(0, 0, 0, 255)

  setFillColor(fillColor)
  setStrokeColor(strokeColor)

  override def getCanvas: Canvas = canvas
  override def getWidth: Double = canvas.getWidth
  override def getHeight: Double = canvas.getHeight
  override def getFillColor: AppColor = fillColor
  override def getStrokeColor: AppColor = strokeColor

  override def setFillColor(color: AppColor): AppColor = {
    fillColor = color
    context.setFill(AppFxCanvas.toFxColor(color))
    color
  }

  override def setStrokeColor(color: AppColor): AppColor = {
    strokeColor = color
    context.setStroke(AppFxCanvas.toFxColor(color))
    color
  }

  override def drawRect(x: Double, y: Double, width: Double, height: Double, strokeWidth: Double): Unit =
    withStrokeWidth(strokeWidth)(context.strokeRect(x, y, width, height))

  override def fillRect(x: Double, y: Double, width: Double, height: Double): Unit = context.fillRect(x, y, width, height)

  override def drawArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double, strokeWidth: Double): Unit =
    withStrokeWidth(strokeWidth)(context.strokeArc(x - diameter / 2, y - diameter / 2, diameter, diameter,
      90 - startAngle, -arcAngle, ArcType.OPEN))

  override def fillArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double): Unit =
    context.fillArc(x - diameter / 2, y - diameter / 2, diameter, diameter, 90 - startAngle, -arcAngle, ArcType.ROUND)

  override def drawLine(x1: Double, y1: Double, x2: Double, y2: Double, strokeWidth: Double): Unit =
    withStrokeWidth(strokeWidth)(context.strokeLine(x1, y1, x2, y2))

  override def drawCubicBezier(startX: Double, startY: Double, control1X: Double, control1Y: Double,
      control2X: Double, control2Y: Double, endX: Double, endY: Double,
      strokeWidth: Double, dashPattern: Option[Seq[Double]]): Unit = {
    context.save()
    context.setLineWidth(strokeWidth)
    dashPattern.filter(_.nonEmpty).foreach(pattern => context.setLineDashes(pattern.map(Double.box)*))
    context.beginPath()
    context.moveTo(startX, startY)
    context.bezierCurveTo(control1X, control1Y, control2X, control2Y, endX, endY)
    context.stroke()
    context.restore()
  }

  override def drawCircle(x: Double, y: Double, diameter: Double, strokeWidth: Double): Unit =
    drawArc(x, y, diameter, 0, 360, strokeWidth)
  override def fillCircle(x: Double, y: Double, diameter: Double): Unit = fillArc(x, y, diameter, 0, 360)

  override def drawImage(x: Double, y: Double, width: Double, height: Double, image: AppImage, alphaUpTo255: Double): Unit = {
    context.save()
    context.setGlobalAlpha(math.max(0, math.min(255, alphaUpTo255)) / 255.0)
    context.drawImage(new Image(image.imageSourceString), x, y, width, height)
    context.restore()
  }

  override def setFont(font: AppFont): Unit = context.setFont(Font.font(font.name,
    if (font.bold) FontWeight.BOLD else FontWeight.NORMAL,
    if (font.italic) FontPosture.ITALIC else FontPosture.REGULAR, font.sizeInPx))

  override def drawStringCentered(x: Double, y: Double, content: String): Unit = {
    context.save()
    context.setTextAlign(TextAlignment.CENTER)
    context.setTextBaseline(VPos.CENTER)
    context.fillText(content, x, y)
    context.restore()
  }

  override def clear(clearColor: AppColor): Unit = {
    val previous = fillColor
    setFillColor(clearColor)
    fillRect(0, 0, getWidth, getHeight)
    setFillColor(previous)
  }

  private def withStrokeWidth(width: Double)(draw: => Unit): Unit = {
    val previous = context.getLineWidth
    if (width >= 0) context.setLineWidth(width)
    draw
    context.setLineWidth(previous)
  }
}

object AppFxCanvas {
  private[canvas] def toFxColor(color: AppColor): Color = {
    val rgb = color.toRGB
    Color.rgb(rgb.red, rgb.green, rgb.blue, rgb.alpha / 255.0)
  }
}
