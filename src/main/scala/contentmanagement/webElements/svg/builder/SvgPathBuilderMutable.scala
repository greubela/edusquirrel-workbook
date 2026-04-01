package contentmanagement.webElements.svg.builder

import contentmanagement.webElements.svg.atomarElements.*

import scala.collection.mutable
import contentmanagement.webElements.svg.atomarElements.AppPathSvgElement
import contentmanagement.webElements.svg.builder.*
import contentmanagement.webElements.svg.builder.SvgPathBuilderCommand.*
import datastructures.core.geometry.{Bounds, Dimension, Point}

import scala.collection.mutable

case class SvgPathBuilderMutable[T: Fractional](override val absStartPoint: Point[T]) extends SvgPathBuilder[T] {

  private val N = summon[Fractional[T]]

  import N.*

  def toSvgPathD: String = pathD.toString()

  private val pathD: StringBuilder = new StringBuilder()
    .append(s"M ${num(absStartPoint.x.toDouble)} ${num(absStartPoint.y.toDouble)}")

  def current: Point[T] = cornerPoints.last

  def pathPoints: List[Point[T]] = cornerPoints.toList
  private val cornerPoints = mutable.ListBuffer[Point[T]](absStartPoint)
  private val controlLines = mutable.ListBuffer[AppLineSvgElement[T]]()

  // --- Helpers ---------------------------------------------------------------


  private def pointStr(p: Point[T]): String =
    num(p.x.toDouble) + " " + num(p.y.toDouble)

  private def num(x: Double): String =
    BigDecimal(x).bigDecimal.stripTrailingZeros.toPlainString

  private def flag(b: Boolean): String = if (b) "1" else "0"

  // --- M / m (moveto) --------------------------------------------------------

  private def append(str: String): this.type = {
    pathD.append(str)
    this
  }

  def moveToAbs(endPoint: Point[T]): this.type = {
    cornerPoints += endPoint
    append(s" M ${pointStr(endPoint)}")
  }

  def calcNextPoint(dimension: Dimension[T]): Point[T] = cornerPoints.last.withDimension(dimension).endPoint

  /** m: relative move */
  def moveToRel(dimension: Dimension[T]): this.type = moveToAbs(calcNextPoint(dimension))

  // --- Z / z (closepath) -----------------------------------------------------

  /** Z: close path */
  def closePath(): this.type = append(" Z")

  // --- L / l (lineto) --------------------------------------------------------

  /** L: absolute line to one point */
  def lineToAbs(point: Point[T]): this.type = {
    cornerPoints += point
    append(s" L ${pointStr(point)}")
  }

  /** l: relative line to one point */
  def lineToRel(dimension: Dimension[T]): this.type = lineToAbs(calcNextPoint(dimension))

  // --- H / h (horizontal lineto) --------------------------------------------

  /** H: absolute horizontal line */
  def horizontalLineWithWidth(width: T): this.type =
    lineToAbs(calcNextPoint(Dimension[T](width, fromInt(0))))

  /** V: absolute vertical line */
  def verticalLineWithHeight(height: T): this.type =
    lineToAbs(calcNextPoint(Dimension[T](fromInt(0), height)))


  // --- C / c (cubic Bézier) --------------------------------------------------

  /** C: absolute cubic Bézier */
  def cubicBezierToAbs(controlPointStart: Point[T],
                       controlPointEnd: Point[T],
                       endPoint: Point[T]): this.type = {
    controlLines += AppLineSvgElement[T](cornerPoints.last, controlPointStart)
    cornerPoints += endPoint
    controlLines += AppLineSvgElement[T](cornerPoints.last, controlPointEnd)

    append(s" C ${pointStr(controlPointStart)} ${pointStr(controlPointEnd)} ${pointStr(endPoint)}")
  }

  def cubicBezierToRel(controlDimensionStart: Dimension[T],
                       controlDimensionEnd: Dimension[T],
                       endPointDimension: Dimension[T]): this.type = {
    cubicBezierToAbs(
      current.withDimension(controlDimensionStart).endPoint,
      current.withDimension(controlDimensionEnd).endPoint,
      current.withDimension(endPointDimension).endPoint)
  }


  /** Q: absolute quadratic Bézier */
  def quadraticBezierToAbs(controlPoint: Point[T], endPoint: Point[T]): this.type = {
    controlLines += AppLineSvgElement[T](cornerPoints.last, controlPoint)
    cornerPoints += endPoint
    controlLines += AppLineSvgElement[T](cornerPoints.last, controlPoint)
    append(s" Q ${pointStr(controlPoint)} ${pointStr(endPoint)}")
  }

  def quadraticBezierWithRel(controlDimension: Dimension[T], endPointDimension: Dimension[T]): this.type = {
    quadraticBezierToAbs(current.withDimension(controlDimension).endPoint, current.withDimension(endPointDimension).endPoint)
  }

  // arcs

  def arcToAbs(radiusX: T,
               radiusY: T,
               xAxisRotationDeg: T,
               largeArc: Boolean,
               sweep: Boolean,
               endPoint: Point[T]): this.type = {
    cornerPoints += endPoint
    append(
      s" A ${num(N.toDouble(radiusX))},${num(N.toDouble(radiusY))} ${num(N.toDouble(xAxisRotationDeg))} ${flag(largeArc)},${flag(sweep)} ${pointStr(endPoint)}"
    )
  }

  def arcToRel(radiusX: T,
               radiusY: T,
               xAxisRotationDeg: T,
               largeArc: Boolean,
               sweep: Boolean,
               dimension: Dimension[T]): this.type = {
    val endPoint = calcNextPoint(dimension)
    arcToAbs(radiusX, radiusY, xAxisRotationDeg, largeArc, sweep, endPoint)
  }

  def addArcToTheTopMoveRight(radius: T): this.type = {
    val dia = radius * fromInt(2)
    arcToRel(radius, radius, fromInt(0), largeArc = true, sweep = true, Dimension(dia, fromInt(0)))
  }

  def addArcToTheRightMoveBottom(radius: T): this.type = {
    val dia = radius * fromInt(2)
    arcToRel(radius, radius, fromInt(0), largeArc = true, sweep = true, Dimension(fromInt(0), dia))
  }

  def addCenteredCircle(radius: T): this.type = {
    controlLines += AppLineSvgElement[T](current, new Point[T](current.x + radius, current.y))
    val dia = radius * fromInt(2)
    this
      .moveToRel(new Dimension[T](-radius, fromInt(0)))
      .arcToRel(radius, radius, fromInt(0), largeArc = true, sweep = false, Dimension(dia, fromInt(0)))
      .arcToRel(radius, radius, fromInt(0), largeArc = true, sweep = false, Dimension(-dia, fromInt(0)))
     // .closePath()
      .moveToRel(new Dimension[T](radius, fromInt(0)))
  }

  def drawBoundRectangle(bounds: Bounds[T]): this.type = {
    this.horizontalLineWithWidth(bounds.width).verticalLineWithHeight(bounds.height).horizontalLineWithWidth(-bounds.width).verticalLineWithHeight(-bounds.height).closePath()
  }

  def addControlFlowConnector(segmentWidth: T, invertHeight: Boolean = false): this.type = {
    val N = summon[Fractional[T]]
    import N.*

    val segmentHeight = if (invertHeight) -segmentWidth else segmentWidth

    this
      .lineToRel(Dimension(segmentWidth, fromInt(0)))
      .lineToRel(Dimension(segmentWidth, segmentHeight))
      .lineToRel(Dimension(segmentWidth, fromInt(0)))
      .lineToRel(Dimension(segmentWidth, fromInt(0)))
      .lineToRel(Dimension(segmentWidth, -segmentHeight))
      .lineToRel(Dimension(segmentWidth, fromInt(0)))
  }

  def toAppSvgElement(): AppPathSvgElement[T] =
    AppPathSvgElement[T](pathD.toString(), cornerPoints.toList, controlLines.toList)


  def moveWholePath(dimension: Dimension[T]): SvgPathBuilder[T] = ???

}