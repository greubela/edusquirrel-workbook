package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderCommand.*


object SvgPathBuilderImmutable {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[SvgPathBuilderImmutable[T]] =
    upickle.default.readwriter[(StartPathCommand[T], List[SvgPathBuilderCommand[T]])].bimap(value => (value.startCommand, value.furtherCommands), value => SvgPathBuilderImmutable[T](value._1, value._2))

  
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
    val (_, _, reversed) = furtherCommands.foldLeft((absStartPoint, absStartPoint, List.empty[AbsoluteCommand[T]])) {
      case ((current, subpathStart, converted), command) =>
        val absolute = command match {
          case _: ClosePath[?] => AddControlLinesCommand(subpathStart, Nil)
          case other => other.toAbsoluteCommand(current)
        }
        val nextStart = command match {
          case _: MoveAbs[?] | _: MoveRel[?] => absolute.positionAfterCommand
          case _ => subpathStart
        }
        (absolute.positionAfterCommand, nextStart, absolute :: converted)
    }
    reversed.reverse
  }

  lazy val relativeCommands: List[RelativeCommand[T]] = {
    val (_, reversed) = furtherCommands.zip(absoluteCommands).foldLeft((absStartPoint, List.empty[RelativeCommand[T]])) {
      case ((current, converted), (command, absolute)) =>
        val relative = command match {
          case _: ClosePath[?] => AddControlLinesCommand(absolute.positionAfterCommand, Nil)
          case other => other.toRelativeCommand(current)
        }
        (absolute.positionAfterCommand, relative :: converted)
    }
    reversed.reverse
  }

  def moveToRel(dimension: Dimension[T]): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ MoveRel(dimension))

  def moveToAbs(endPoint: Point[T]): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ MoveAbs(endPoint))

  def closePath(): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ ClosePath[T]())

  def lineToAbs(point: Point[T]): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ LineAbs(point))

  def lineToRel(dimension: Dimension[T]): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ LineRel(dimension))

  def horizontalLineWithWidth(width: T): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ HorizontalRel(width))

  def verticalLineWithHeight(height: T): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ VerticalRel(height))

  def cubicBezierToAbs(cpStart: Point[T], cpEnd: Point[T], end: Point[T]): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ CubicAbs(cpStart, cpEnd, end))

  def cubicBezierToRel(cpStart: Dimension[T], cpEnd: Dimension[T], end: Dimension[T]): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ CubicRel(cpStart, cpEnd, end))

  def quadraticBezierToAbs(controlPoint: Point[T], endPoint: Point[T]): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ QuadAbs(controlPoint, endPoint))

  def quadraticBezierWithRel(controlDimension: Dimension[T], endPointDimension: Dimension[T]): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ QuadRel(controlDimension, endPointDimension))

  def arcToAbs(radiusX: T,
               radiusY: T,
               xAxisRotationDeg: T,
               largeArc: Boolean,
               sweep: Boolean,
               endPoint: Point[T]): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ ArcAbs(radiusX, radiusY, xAxisRotationDeg, largeArc, sweep, endPoint))

  def arcToRel(radiusX: T,
               radiusY: T,
               xAxisRotationDeg: T,
               largeArc: Boolean,
               sweep: Boolean,
               dimension: Dimension[T]): SvgPathBuilderImmutable[T] =
    copy(furtherCommands = furtherCommands :+ ArcRel(radiusX, radiusY, xAxisRotationDeg, largeArc, sweep, dimension))

  def addArcToTheTopMoveRight(radius: T): SvgPathBuilderImmutable[T] = {
    val dia = radius * fromInt(2)
    arcToRel(radius, radius, fromInt(0), largeArc = true, sweep = true, Dimension(dia, fromInt(0)))
  }

  def addArcToTheRightMoveBottom(radius: T): SvgPathBuilderImmutable[T] = {
    val dia = radius * fromInt(2)
    arcToRel(radius, radius, fromInt(0), largeArc = true, sweep = true, Dimension(fromInt(0), dia))
  }

  def addCenteredCircle(radius: T): SvgPathBuilderImmutable[T] = {
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

  def drawBoundRectangle(bounds: Bounds[T]): SvgPathBuilderImmutable[T] = {
    val w = bounds.width;
    val h = bounds.height
    copy(furtherCommands = furtherCommands ++ List(
      HorizontalRel(w), VerticalRel(h), HorizontalRel(-w), VerticalRel(-h), ClosePath[T]()
    ))
  }

  def addControlFlowConnector(segmentWidth: T, invertHeight: Boolean = false): SvgPathBuilderImmutable[T] = {
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

  lazy val current: Point[T] = absoluteCommands.lastOption.map(_.positionAfterCommand).getOrElse(absStartPoint)

  def calcNextPoint(dimension: Dimension[T]): Point[T] =
    current.withDimension(dimension).endPoint

  def moveWholePath(delta: Dimension[T]): SvgPathBuilderImmutable[T] = {

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
