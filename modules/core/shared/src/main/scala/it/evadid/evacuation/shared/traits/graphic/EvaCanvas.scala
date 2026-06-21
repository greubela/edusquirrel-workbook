package it.evadid.evacuation.shared.traits.graphic

import it.evadid.evacuation.core.graphic.model.{EvaColor, EvaFont, EvaImage}

trait EvaCanvas[C] {

  def getCanvasElement: C

  def getDrawingWidth: Double

  def getDrawingHeight: Double


  def setColor(color: EvaColor) = {
    setFillColor(color)
    setStrokeColor(color)
  }

  def setFillColor(color: EvaColor): EvaColor

  def getFillColor: EvaColor

  def getStrokeColor: EvaColor


  def setStrokeColor(color: EvaColor): EvaColor


  def drawRect(x: Double, y: Double, width: Double, height: Double): Unit

  def fillRect(x: Double, y: Double, width: Double, height: Double): Unit

  /**
   * Draws an Arc on the Canvas
   * @param x the x-Position of the circle-center
   * @param y the y-Position of the circle-center
   * @param diameter the diameter of the circle
   * @param startAngle the start angle (0-360, 0 = top)
   * @param arcAngle the arc angle (0-360, positive = clockwise)
   * @param strokeWidth the width of the stroke (-1 = default)
   */
    def drawArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double, strokeWidth: Double = -1): Unit

  /**
   * Fills an Arc on the Canvas
   * @param x the x-Position of the circle-center
   * @param y the y-Position of the circle-center
   * @param diameter the diameter of the circle
   * @param startAngle the start angle (0-360, 0 = top)
   * @param arcAngle the arc angle (0-360, positive = clockwise)
   */
    def fillArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double): Unit


  def drawLine(x1: Double, y1: Double, x2: Double, y2: Double, width: Double = 1): Unit

  /*
  def drawCircle(x: Double, y: Double, diameter: Double): Unit = drawArc(x, y, diameter, diameter, 0, 360): Unit

  def fillCircle(x: Double, y: Double, diameter: Double): Unit = fillArc(x, y, diameter, diameter, 0, 360): Unit
  */

  def drawCircle(x: Double, y: Double, diameter: Double, width: Double = 1): Unit

  def fillCircle(x: Double, y: Double, diameter: Double): Unit


  def drawImage(x: Double, y: Double, img: EvaImage): Unit

  def drawScaledImage(x: Double, y: Double, width: Double, height: Double, img: EvaImage): Unit

  def drawImageWithAlpha(x: Double, y: Double, alphaUpTo255: Double, img: EvaImage): Unit

  def setFont(font: EvaFont): Unit

  def getTextWidth(str: String, font: EvaFont = null): Double

  def drawStringCentered(x: Double, y: Double, content: String): Unit

  /*
  def getScalingFactor(): Double

  def setScalingFactor(scalingFactor: Double)

   */


  def clear(): Unit = {
    val oldColor = getFillColor
    setFillColor(EvaColor(255, 255, 255, 255))
    fillRect(0, 0, getDrawingWidth, getDrawingHeight)
    setFillColor(oldColor)
  }

  def addMouseListener(mL: EvaMouseListener): Unit

  def removeMouseListener(mL: EvaMouseListener): Unit

}
