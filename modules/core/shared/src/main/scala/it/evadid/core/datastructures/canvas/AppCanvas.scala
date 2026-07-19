package it.evadid.core.datastructures.canvas

import it.evadid.core.datastructures.color.AppColor
import it.evadid.core.datastructures.font.AppFont

/** Cross-platform drawing surface implemented by the JS and JVM core modules. */
trait AppCanvas[C] {
  def getCanvas: C
  def getWidth: Double
  def getHeight: Double

  def setColor(color: AppColor): AppColor = {
    setFillColor(color)
    setStrokeColor(color)
  }

  def setFillColor(color: AppColor): AppColor
  def getFillColor: AppColor
  def setStrokeColor(color: AppColor): AppColor
  def getStrokeColor: AppColor
  def drawRect(x: Double, y: Double, width: Double, height: Double, strokeWidth: Double = 1): Unit
  def fillRect(x: Double, y: Double, width: Double, height: Double): Unit
  def drawArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double, strokeWidth: Double = 1): Unit
  def fillArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double): Unit
  def drawLine(x1: Double, y1: Double, x2: Double, y2: Double, strokeWidth: Double = 1): Unit
  def drawCubicBezier(startX: Double, startY: Double, control1X: Double, control1Y: Double,
      control2X: Double, control2Y: Double, endX: Double, endY: Double,
      strokeWidth: Double = 1, dashPattern: Option[Seq[Double]] = None): Unit
  def drawCircle(x: Double, y: Double, diameter: Double, strokeWidth: Double = 1): Unit
  def fillCircle(x: Double, y: Double, diameter: Double): Unit
  def drawImage(x: Double, y: Double, width: Double, height: Double, image: AppImage, alphaUpTo255: Double = 255): Unit
  def setFont(font: AppFont): Unit
  def drawStringCentered(x: Double, y: Double, content: String): Unit
  def clear(clearColor: AppColor): Unit
}

object AppCanvas {
  def calcArcPoint(x: Double, y: Double, diameter: Double, angle: Double): (Double, Double) = {
    val radius = diameter / 2.0
    val xPos = x + radius * Math.sin(angle / 360.0 * Math.PI * 2)
    val yPos = y - radius * Math.cos(angle / 360.0 * Math.PI * 2)
    (xPos, yPos)
  }
}
