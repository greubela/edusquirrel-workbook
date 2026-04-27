package workbook.htmlElements.basic.canvas

import contentmanagement.webElements.HtmlAppElement
import datastructures.web.file.FullImage
import datastructures.web.font.AppFont
import it.evadid.core.datastructures.color.AppColor
trait AppCanvas[C] extends HtmlAppElement {
  
  def getCanvas: C

  def getWidth: Double

  def getHeight: Double

  def setColor(color: AppColor) = {
    setFillColor(color)
    setStrokeColor(color)
  }

  def setFillColor(color: AppColor): AppColor

  def getFillColor: AppColor

  def getStrokeColor: AppColor


  def setStrokeColor(color: AppColor): AppColor


  def drawRect(x: Double, y: Double, width: Double, height: Double, strokeWidth: Double = 1): Unit

  def fillRect(x: Double, y: Double, width: Double, height: Double): Unit

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
  def drawArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double, strokeWidth: Double = 1): Unit

  /**
   * Fills an Arc on the Canvas
   *
   * @param x          the x-Position of the circle-center
   * @param y          the y-Position of the circle-center
   * @param diameter   the diameter of the circle
   * @param startAngle the start angle (0-360, 0 = top)
   * @param arcAngle   the arc angle (0-360, positive = clockwise)
   */
  def fillArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double): Unit

  def drawLine(x1: Double, y1: Double, x2: Double, y2: Double, strokeWidth: Double = 1): Unit

  def drawCubicBezier(
      startX: Double,
      startY: Double,
      control1X: Double,
      control1Y: Double,
      control2X: Double,
      control2Y: Double,
      endX: Double,
      endY: Double,
      strokeWidth: Double = 1,
      dashPattern: Option[Seq[Double]] = None
  ): Unit

  def drawCircle(x: Double, y: Double, diameter: Double, strokeWidth: Double = 1): Unit

  def fillCircle(x: Double, y: Double, diameter: Double): Unit

  def drawImage(x: Double, y: Double, width: Double, height: Double, img: FullImage, alphaUpTo255: Double = 255): Unit
  
  def setFont(font: AppFont): Unit

  def drawStringCentered(x: Double, y: Double, content: String): Unit

  /*
  def getScalingFactor(): Double

  def setScalingFactor(scalingFactor: Double)

   */


  def clear(clearColor: AppColor): Unit

}

object AppCanvas {

  /**
   * Calculates the position of a point on an arc of a circle with the given parameters
   *
   * @param x       the center of the circle
   * @param y       the center of the circle
   * @param radiusX the x radius of the ellipse
   * @param radiusY the y radius of the ellipse
   * @param angle   the angle position (360 degress, 0 = top, 90 = right)
   * @return
   */
  def calcArcPoint(x: Double, y: Double, diameter: Double, angle: Double): (Double, Double) = {
    val radius = diameter / 2.0
    val xPos = x + radius * Math.sin(angle / 360.0 * Math.PI * 2)
    val yPos = y - radius * Math.cos(angle / 360.0 * Math.PI * 2)
    (xPos, yPos)
  }

}

