package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderCommand.*

import scala.collection.mutable

object SvgPathBuilderImmutable {
  
  def apply[T: Fractional](start: Point[T]): SvgPathBuilderImmutable[T] = SvgPathBuilderImmutable[T](StartPathCommand(start), List())
  
}

case class SvgPathBuilderImmutable[T: Fractional](
                                                   startCommand: StartPathCommand[T],
                                                   furtherCommands: List[SvgPathBuilderCommand[T]]
                                                 ) extends SvgPathBuilder {
  private val N = summon[Fractional[T]];

  import N.*

  override lazy val pathPoints: List[Point[T]] = List(startCommand.absoluteStartPos) ++ absoluteCommands.map(_.positionAfterCommand)

  lazy val absStartPoint: Point[T] = startCommand.absoluteStartPos

  lazy val absoluteCommands: List[AbsoluteCommand[T]] = {
    var curPoint = startCommand.absoluteStartPos
    var res = mutable.ListBuffer[AbsoluteCommand[T]]()
    for (curCommand <- furtherCommands) {
      val absCommand = curCommand.toAbsoluteCommand(curPoint)
      res += absCommand
      curPoint = absCommand.positionAfterCommand
    }
    res.toList
  }

  lazy val relativeCommands: List[RelativeCommand[T]] = {
    var curPoint = startCommand.absoluteStartPos
    var res = mutable.ListBuffer[RelativeCommand[T]]()
    for (curCommand <- furtherCommands) {
      val relCommand = curCommand.toRelativeCommand(curPoint)
      res += relCommand
      curPoint = curCommand.toAbsoluteCommand(curPoint).positionAfterCommand
    }
    res.toList
  }

  def moveToRel(dimension: Dimension[T]): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ MoveRel(dimension))

  def moveToAbs(endPoint: Point[T]): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ MoveAbs(endPoint))

  def closePath(): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ ClosePath[T]())

  def lineToAbs(point: Point[T]): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ LineAbs(point))

  def lineToRel(dimension: Dimension[T]): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ LineRel(dimension))

  def horizontalLineWithWidth(width: T): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ HorizontalRel(width))

  def verticalLineWithHeight(height: T): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ VerticalRel(height))

  def cubicBezierToAbs(cpStart: Point[T], cpEnd: Point[T], end: Point[T]): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ CubicAbs(cpStart, cpEnd, end))

  def cubicBezierToRel(cpStart: Dimension[T], cpEnd: Dimension[T], end: Dimension[T]): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ CubicRel(cpStart, cpEnd, end))

  def quadraticBezierToAbs(controlPoint: Point[T], endPoint: Point[T]): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ QuadAbs(controlPoint, endPoint))

  def quadraticBezierWithRel(controlDimension: Dimension[T], endPointDimension: Dimension[T]): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ QuadRel(controlDimension, endPointDimension))

  def arcToAbs(radiusX: T,
               radiusY: T,
               xAxisRotationDeg: T,
               largeArc: Boolean,
               sweep: Boolean,
               endPoint: Point[T]): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ ArcAbs(radiusX, radiusY, xAxisRotationDeg, largeArc, sweep, endPoint))

  def arcToRel(radiusX: T,
               radiusY: T,
               xAxisRotationDeg: T,
               largeArc: Boolean,
               sweep: Boolean,
               dimension: Dimension[T]): SvgPathBuilder[T] =
    copy(furtherCommands = furtherCommands :+ ArcRel(radiusX, radiusY, xAxisRotationDeg, largeArc, sweep, dimension))

  def addArcToTheTopMoveRight(radius: T): SvgPathBuilder[T] = {
    val dia = radius * fromInt(2)
    arcToRel(radius, radius, fromInt(0), largeArc = true, sweep = true, Dimension(dia, fromInt(0)))
  }

  def addArcToTheRightMoveBottom(radius: T): SvgPathBuilder[T] = {
    val dia = radius * fromInt(2)
    arcToRel(radius, radius, fromInt(0), largeArc = true, sweep = true, Dimension(fromInt(0), dia))
  }

  def addCenteredCircle(radius: T): SvgPathBuilder[T] = {
    val dia = radius * fromInt(2)
    copy(furtherCommands = furtherCommands ++ List(
      MoveRel(Dimension(-radius, fromInt(0))),
      ArcRel(radius, radius, fromInt(0), largeArc = true, sweep = false, Dimension(dia, fromInt(0))),
      ArcRel(radius, radius, fromInt(0), largeArc = true, sweep = false, Dimension(-dia, fromInt(0))),
    //  ClosePath[T](),
      MoveRel(Dimension(radius, fromInt(0))),
      CenteredCircleControl(radius)
    ))
  }

  def drawBoundRectangle(bounds: Bounds[T]): SvgPathBuilder[T] = {
    val w = bounds.width;
    val h = bounds.height
    copy(furtherCommands = furtherCommands ++ List(
      HorizontalRel(w), VerticalRel(h), HorizontalRel(-w), VerticalRel(-h), ClosePath[T]()
    ))
  }

  def addControlFlowConnector(segmentWidth: T, invertHeight: Boolean = false): SvgPathBuilder[T] = {
    val segH = if (invertHeight) -segmentWidth else segmentWidth
    copy(furtherCommands = furtherCommands ++ List(
      LineRel(Dimension(segmentWidth, fromInt(0))),
      LineRel(Dimension(segmentWidth, segH)),
      LineRel(Dimension(segmentWidth, fromInt(0))),
      LineRel(Dimension(segmentWidth, fromInt(0))),
      LineRel(Dimension(segmentWidth, -segH)),
      LineRel(Dimension(segmentWidth, fromInt(0)))
    ))
  }

  lazy val toSvgPathD: String = {
    val head = s"M ${num(startCommand.absoluteStartPos.x.toDouble)} ${num(startCommand.absoluteStartPos.y.toDouble)}"
    head + furtherCommands.map(_.getPathDString()).mkString
  }

  lazy val current: Point[T] = absoluteCommands.last.positionAfterCommand

  def calcNextPoint(dimension: Dimension[T]): Point[T] =
    current.withDimension(dimension).endPoint

  def moveWholePath(delta: Dimension[T]): SvgPathBuilder[T] = {

    def shift(p: Point[T]) = p.moveWithDimension(delta)

    val shifted = furtherCommands.map {
      case MoveAbs(p) => MoveAbs(shift(p))
      case LineAbs(p) => LineAbs(shift(p))
      case CubicAbs(c1, c2, e) => CubicAbs(shift(c1), shift(c2), shift(e))
      case QuadAbs(cp, e) => QuadAbs(shift(cp), shift(e))
      case ArcAbs(rx, ry, rot, large, sweep, end) => ArcAbs(rx, ry, rot, large, sweep, shift(end))
      case AddControlLinesCommand(pred, cps) => AddControlLinesCommand(shift(pred), cps.map(shift))
      case other => other
    }
    copy(
      startCommand = StartPathCommand(shift(startCommand.absoluteStartPos)),
      furtherCommands = shifted
    )
  }


  private def num(x: Double): String =
    BigDecimal(x).bigDecimal.stripTrailingZeros.toPlainString
}