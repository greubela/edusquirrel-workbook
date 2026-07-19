package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.util.logging.Logger

case class SvgPathBuilderRelativeCoords[T: Fractional](logger: Logger, baseBuilder: SvgPathBuilder[T], maxDimension: Dimension[T]) {

  val N = summon[Fractional[T]]

  import N.*

  val hundred: T = fromInt(100)

  def intToT(intValue: Int): T = fromInt(intValue)

  def doubleToT(doubleValue: Double, divideBy: Int = 1, maxDiv: Int = 10000000): T = {
    val asInt = doubleValue.toInt
    val diffToInt: Double = doubleValue - doubleValue.toInt
    if (diffToInt > 1 || diffToInt < 1) throw IllegalArgumentException(s"Double ${doubleValue} cannot be converted to [T] because it is too large for an int!")
    else if (diffToInt == 0 || divideBy >= maxDiv) fromInt(asInt) / fromInt(divideBy)
    else doubleToT(doubleValue / 10.0, divideBy * 10, maxDiv)
  }

  def percTransX(percentageOfWidthMax100: Double): T = doubleToT(percentageOfWidthMax100) / hundred * maxWidth

  def percTransY(percentageOfHeightMax100: Double): T = doubleToT(percentageOfHeightMax100) / hundred * maxHeight

  def percTansPos(percentageOfWidthMax100: Double, percentageOfHeightMax100: Double): Point[T] = Point(percTransX(percentageOfWidthMax100), percTransY(percentageOfHeightMax100))

  def percTransDim(percentageOfWidthMax100: Double, percentageOfHeightMax100: Double): Dimension[T] = Dimension(percTransX(percentageOfWidthMax100), percTransY(percentageOfHeightMax100))

  def maxWidth: T = maxDimension.width

  def maxHeight: T = maxDimension.height


  private def checkDim(relativeXCoordMax100: Double, relativeYCoordMax100: Double): Unit = {
    if (relativeXCoordMax100 > 100 || relativeYCoordMax100 > 100) logger.logWarn(s"[WARN] SvgPathBuilderForMaxDimension: Drawing out of bounds (> 100): (${relativeXCoordMax100}|${relativeYCoordMax100})!")
    if (relativeXCoordMax100 < 0 || relativeYCoordMax100 < 0) logger.logWarn("[WARN] SvgPathBuilderForMaxDimension: Drawing out of bounds (negative): (${relativeXCoordMax100}|${relativeYCoordMax100})!")
  }

  def update(func: SvgPathBuilder[T] => SvgPathBuilder[T]): SvgPathBuilderRelativeCoords[T] = this.copy(baseBuilder = func(baseBuilder))


  def moveToRel(relativeXCoordMax100: Double, relativeYCoordMax100: Double): SvgPathBuilderRelativeCoords[T] = {
    checkDim(relativeXCoordMax100, relativeYCoordMax100)
    update(_.moveToRel(percTransDim(relativeYCoordMax100, relativeYCoordMax100)))
  }

  def lineToRel(relativeXCoordMax100: Double, relativeYCoordMax100: Double): SvgPathBuilderRelativeCoords[T] = {
    checkDim(relativeXCoordMax100, relativeYCoordMax100)
    update(_.lineToRel(percTransDim(relativeYCoordMax100, relativeYCoordMax100)))
  }

  def cubicBezierToRel(rControlStartX: Double, rControlStartY: Double, rControlEndX: Double, rControlEndY: Double, rEndPointX: Double, rEndPointY: Double): SvgPathBuilderRelativeCoords[T] = {
    checkDim(rControlStartX, rControlStartY)
    checkDim(rControlEndX, rControlEndY)
    checkDim(rEndPointX, rEndPointY)
    update(_.cubicBezierToRel(percTransDim(rControlStartX, rControlStartY), percTransDim(rControlEndX, rControlEndY), percTransDim(rEndPointX, rEndPointY)))
  }

  def quadraticBezierWithRel(rControlX: Double, rControlY: Double, rEndPointX: Double, rEndPointY: Double): SvgPathBuilderRelativeCoords[T] = {
    checkDim(rControlY, rControlX)
    checkDim(rEndPointX, rEndPointY)
    update(_.quadraticBezierWithRel(percTransDim(rControlX, rControlY), percTransDim(rEndPointX, rEndPointY)))
  }

  def arcToRel(rRadiusX: Double, rRadiusY: Double, rEndPointX: Double, rEndPointY: Double, rotationDegreesMax360: Double, largeArc: Boolean, sweep: Boolean): SvgPathBuilderRelativeCoords[T] = {
    checkDim(2 * rRadiusX, 2 * rRadiusY)
    checkDim(rEndPointX, rEndPointY)
    val rotDeg = doubleToT(rotationDegreesMax360)
    update(_.arcToRel(percTransX(rRadiusX), percTransY(rRadiusY), rotDeg, largeArc, sweep, percTransDim(rEndPointX, rEndPointY)))
  }

}
