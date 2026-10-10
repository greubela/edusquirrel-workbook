package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderCommand.{AbsoluteCommand, RelativeCommand}

trait SvgPathBuilderCommand[T: Fractional] {

  def getPathDString(): String

  def toRelativeCommand(startPosition: Point[T]): RelativeCommand[T]

  def toAbsoluteCommand(startPosition: Point[T]): AbsoluteCommand[T]
}

object SvgPathBuilderCommand {

  case class StartPathCommand[T: Fractional](absoluteStartPos: Point[T])

  trait AbsoluteCommand[T: Fractional] extends SvgPathBuilderCommand[T] {
    def positionAfterCommand: Point[T]
    def controlPointsAbsolute: List[Point[T]] = List()
    def toAbsoluteCommand(startPosition: Point[T]): AbsoluteCommand[T] = this
  }

  trait RelativeCommand[T: Fractional] extends SvgPathBuilderCommand[T] {
    def relativeMovement: Dimension[T]

    def toRelativeCommand(startPosition: Point[T]): RelativeCommand[T] = this
  }

  case class AddControlLinesCommand[T: Fractional](predecessorPos: Point[T], override val controlPointsAbsolute: List[Point[T]])
    extends RelativeCommand[T], AbsoluteCommand[T] {

    def getPathDString(): String = ""

    def positionAfterCommand: Point[T] = predecessorPos

    def relativeMovement: Dimension[T] = Dimension[T](implicitly[Fractional[T]].fromInt(0), implicitly[Fractional[T]].fromInt(0))
  }

  private def num[T: Fractional](x: Double): String =
    BigDecimal(x).bigDecimal.stripTrailingZeros.toPlainString

  private def flag(b: Boolean): String = if (b) "1" else "0"

  private def pt[T: Fractional](p: Point[T]): String = {
    val N = summon[Fractional[T]]
    import N.*
    s"${num(p.x.toDouble)} ${num(p.y.toDouble)}"
  }

  private def dim[T: Fractional](d: Dimension[T]): String = {
    val N = summon[Fractional[T]]
    import N.*
    s"${num(d.width.toDouble)} ${num(d.height.toDouble)}"
  }

  // Move
  case class MoveAbs[T: Fractional](p: Point[T]) extends AbsoluteCommand[T] {
    def getPathDString(): String = s" M ${pt(p)}"

    def positionAfterCommand: Point[T] = p

    override def toRelativeCommand(start: Point[T]): RelativeCommand[T] = {
      val N = summon[Fractional[T]];
      import N.*
      MoveRel(Dimension(p.x - start.x, p.y - start.y))
    }
  }

  case class MoveRel[T: Fractional](d: Dimension[T]) extends RelativeCommand[T] {
    def getPathDString(): String = s" m ${dim(d)}"

    def relativeMovement: Dimension[T] = d

    override def toAbsoluteCommand(start: Point[T]): AbsoluteCommand[T] = MoveAbs(start.moveWithDimension(d))
  }

  // Close
  case class ClosePath[T: Fractional]() extends SvgPathBuilderCommand[T] {
    def getPathDString(): String = " Z"

    def toRelativeCommand(start: Point[T]): RelativeCommand[T] = AddControlLinesCommand(start, Nil)

    def toAbsoluteCommand(start: Point[T]): AbsoluteCommand[T] = AddControlLinesCommand(start, Nil)
  }

  /* ToDo: Close properly
    case class ClosePath[T: Fractional](start: Point[T]) extends SvgPathBuilderCommand[T], RelativeCommand[T], AbsoluteCommand[T] {
      def getPathDString(): String = " Z"

      override def toRelativeCommand(start: Point[T]): RelativeCommand[T] = this

      override def toAbsoluteCommand(start: Point[T]): AbsoluteCommand[T] = this

      //override def relativeMovement: Dimension[T] = start

      //override def positionAfterCommand: Point[T] = ???
    }
     */

  // Lines
  case class LineAbs[T: Fractional](p: Point[T]) extends AbsoluteCommand[T] {
    def getPathDString(): String = s" L ${pt(p)}"

    def positionAfterCommand: Point[T] = p

    override def toRelativeCommand(start: Point[T]): RelativeCommand[T] = {
      val N = summon[Fractional[T]];
      import N.*
      LineRel(Dimension(p.x - start.x, p.y - start.y))
    }
  }

  case class LineRel[T: Fractional](d: Dimension[T]) extends RelativeCommand[T] {
    def getPathDString(): String = s" l ${dim(d)}"

    def relativeMovement: Dimension[T] = d

    override def toAbsoluteCommand(start: Point[T]): AbsoluteCommand[T] = LineAbs(start.moveWithDimension(d))
  }

  // H/V (relative)
  case class HorizontalRel[T: Fractional](w: T) extends RelativeCommand[T] {
    def getPathDString(): String = {
      val N = summon[Fractional[T]];
      import N.*
      s" h ${num(w.toDouble)}"
    }

    def relativeMovement: Dimension[T] = Dimension(w, summon[Fractional[T]].fromInt(0))

    override def toAbsoluteCommand(start: Point[T]): AbsoluteCommand[T] =
      LineAbs(Point(summon[Fractional[T]].plus(start.x, w), start.y))
  }

  case class VerticalRel[T: Fractional](h: T) extends RelativeCommand[T] {
    def getPathDString(): String = {
      val N = summon[Fractional[T]]
      import N.*
      s" v ${num(h.toDouble)}"
    }

    def relativeMovement: Dimension[T] = Dimension(summon[Fractional[T]].fromInt(0), h)

    override def toAbsoluteCommand(start: Point[T]): AbsoluteCommand[T] =
      LineAbs(Point(start.x, summon[Fractional[T]].plus(start.y, h)))
  }

  // Cubic Bézier
  case class CubicAbs[T: Fractional](cp1: Point[T], cp2: Point[T], end: Point[T]) extends AbsoluteCommand[T] {
    def getPathDString(): String = s" C ${pt(cp1)} ${pt(cp2)} ${pt(end)}"

    def positionAfterCommand: Point[T] = end

    override val controlPointsAbsolute: List[Point[T]] = List(cp1, cp2)

    override def toRelativeCommand(start: Point[T]): RelativeCommand[T] = {
      val N = summon[Fractional[T]];
      import N.*
      CubicRel(
        Dimension(cp1.x - start.x, cp1.y - start.y),
        Dimension(cp2.x - start.x, cp2.y - start.y),
        Dimension(end.x - start.x, end.y - start.y)
      )
    }
  }

  case class CubicRel[T: Fractional](cp1: Dimension[T], cp2: Dimension[T], end: Dimension[T]) extends RelativeCommand[T] {
    def getPathDString(): String = s" c ${dim(cp1)} ${dim(cp2)} ${dim(end)}"

    def relativeMovement: Dimension[T] = end

    override def toAbsoluteCommand(start: Point[T]): AbsoluteCommand[T] = {
      val a1 = start.withDimension(cp1).endPoint
      val a2 = start.withDimension(cp2).endPoint
      val e = start.withDimension(end).endPoint
      CubicAbs(a1, a2, e)
    }
  }

  // Quadratic Bézier
  case class QuadAbs[T: Fractional](cp: Point[T], end: Point[T]) extends AbsoluteCommand[T] {
    def getPathDString(): String = s" Q ${pt(cp)} ${pt(end)}"

    def positionAfterCommand: Point[T] = end

    override val controlPointsAbsolute: List[Point[T]] = List(cp)

    override def toRelativeCommand(start: Point[T]): RelativeCommand[T] = {
      val N = summon[Fractional[T]];
      import N.*
      QuadRel(
        Dimension(cp.x - start.x, cp.y - start.y),
        Dimension(end.x - start.x, end.y - start.y)
      )
    }
  }

  case class QuadRel[T: Fractional](cp: Dimension[T], end: Dimension[T]) extends RelativeCommand[T] {
    def getPathDString(): String = s" q ${dim(cp)} ${dim(end)}"

    def relativeMovement: Dimension[T] = end

    override def toAbsoluteCommand(start: Point[T]): AbsoluteCommand[T] = {
      val acp = start.withDimension(cp).endPoint
      val e = start.withDimension(end).endPoint
      QuadAbs(acp, e)
    }
  }

  // Arc
  case class ArcAbs[T: Fractional](
                                   rx: T,
                                   ry: T,
                                   xAxisRotationDeg: T,
                                   largeArc: Boolean,
                                   sweep: Boolean,
                                   end: Point[T]
                                 ) extends AbsoluteCommand[T] {
    def getPathDString(): String = {
      val N = summon[Fractional[T]]
      s" A ${num(N.toDouble(rx))},${num(N.toDouble(ry))} ${num(N.toDouble(xAxisRotationDeg))} ${flag(largeArc)},${flag(sweep)} ${pt(end)}"
    }

    def positionAfterCommand: Point[T] = end

    override def toRelativeCommand(start: Point[T]): RelativeCommand[T] = {
      val N = summon[Fractional[T]]
      import N.*
      ArcRel(
        rx,
        ry,
        xAxisRotationDeg,
        largeArc,
        sweep,
        Dimension(end.x - start.x, end.y - start.y)
      )
    }
  }

  case class ArcRel[T: Fractional](
                                    rx: T, ry: T, xAxisRotationDeg: T, largeArc: Boolean, sweep: Boolean, d: Dimension[T]
                                  ) extends RelativeCommand[T] {
    def getPathDString(): String = {
      val N = summon[Fractional[T]]
      import N.*
      s" a ${num(rx.toDouble)},${num(ry.toDouble)} ${num(xAxisRotationDeg.toDouble)} ${flag(largeArc)},${flag(sweep)} ${dim(d)}"
    }

    def relativeMovement: Dimension[T] = d

    override def toAbsoluteCommand(start: Point[T]): AbsoluteCommand[T] =
      ArcAbs(rx, ry, xAxisRotationDeg, largeArc, sweep, start.moveWithDimension(d))
  }

  // Centered circle helper’s synthetic control line, parameterized by radius
  case class CenteredCircleControl[T: Fractional](radius: T) extends SvgPathBuilderCommand[T] {
    def getPathDString(): String = ""

    def toRelativeCommand(start: Point[T]): RelativeCommand[T] =
      AddControlLinesCommand(start, List(Point(summon[Fractional[T]].plus(start.x, radius), start.y)))

    def toAbsoluteCommand(start: Point[T]): AbsoluteCommand[T] =
      AddControlLinesCommand(start, List(Point(summon[Fractional[T]].plus(start.x, radius), start.y)))
  }

  // Raw append (verbatim d-fragment)
  case class RawAppend[T: Fractional](chunk: String) extends SvgPathBuilderCommand[T] {
    def getPathDString(): String = chunk

    def toRelativeCommand(start: Point[T]): RelativeCommand[T] = AddControlLinesCommand(start, Nil)

    def toAbsoluteCommand(start: Point[T]): AbsoluteCommand[T] = AddControlLinesCommand(start, Nil)
  }


  object AddControlLinesCommand {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[AddControlLinesCommand[T]] =
    upickle.default.readwriter[(Point[T], List[Point[T]])].bimap(value => (value.predecessorPos, value.controlPointsAbsolute), value => AddControlLinesCommand[T](value._1, value._2))
  }

  object ArcAbs {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[ArcAbs[T]] =
    upickle.default.readwriter[(T, T, T, Boolean, Boolean, Point[T])].bimap(value => (value.rx, value.ry, value.xAxisRotationDeg, value.largeArc, value.sweep, value.end), value => ArcAbs[T](value._1, value._2, value._3, value._4, value._5, value._6))
  }

  object ArcRel {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[ArcRel[T]] =
    upickle.default.readwriter[(T, T, T, Boolean, Boolean, Dimension[T])].bimap(value => (value.rx, value.ry, value.xAxisRotationDeg, value.largeArc, value.sweep, value.d), value => ArcRel[T](value._1, value._2, value._3, value._4, value._5, value._6))
  }

  object CenteredCircleControl {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[CenteredCircleControl[T]] =
    upickle.default.readwriter[T].bimap(value => value.radius, value => CenteredCircleControl[T](value))
  }

  object ClosePath {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[ClosePath[T]] =
    upickle.default.readwriter[Unit].bimap(value => (), _ => ClosePath[T]())
  }

  object CubicAbs {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[CubicAbs[T]] =
    upickle.default.readwriter[(Point[T], Point[T], Point[T])].bimap(value => (value.cp1, value.cp2, value.end), value => CubicAbs[T](value._1, value._2, value._3))
  }

  object CubicRel {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[CubicRel[T]] =
    upickle.default.readwriter[(Dimension[T], Dimension[T], Dimension[T])].bimap(value => (value.cp1, value.cp2, value.end), value => CubicRel[T](value._1, value._2, value._3))
  }

  object HorizontalRel {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[HorizontalRel[T]] =
    upickle.default.readwriter[T].bimap(value => value.w, value => HorizontalRel[T](value))
  }

  object LineAbs {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[LineAbs[T]] =
    upickle.default.readwriter[Point[T]].bimap(value => value.p, value => LineAbs[T](value))
  }

  object LineRel {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[LineRel[T]] =
    upickle.default.readwriter[Dimension[T]].bimap(value => value.d, value => LineRel[T](value))
  }

  object MoveAbs {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[MoveAbs[T]] =
    upickle.default.readwriter[Point[T]].bimap(value => value.p, value => MoveAbs[T](value))
  }

  object MoveRel {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[MoveRel[T]] =
    upickle.default.readwriter[Dimension[T]].bimap(value => value.d, value => MoveRel[T](value))
  }

  object QuadAbs {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[QuadAbs[T]] =
    upickle.default.readwriter[(Point[T], Point[T])].bimap(value => (value.cp, value.end), value => QuadAbs[T](value._1, value._2))
  }

  object QuadRel {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[QuadRel[T]] =
    upickle.default.readwriter[(Dimension[T], Dimension[T])].bimap(value => (value.cp, value.end), value => QuadRel[T](value._1, value._2))
  }

  object RawAppend {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[RawAppend[T]] =
    upickle.default.readwriter[String].bimap(value => value.chunk, value => RawAppend[T](value))
  }

  object StartPathCommand {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[StartPathCommand[T]] =
    upickle.default.readwriter[Point[T]].bimap(value => value.absoluteStartPos, value => StartPathCommand[T](value))
  }

  object VerticalRel {
  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[VerticalRel[T]] =
    upickle.default.readwriter[T].bimap(value => value.h, value => VerticalRel[T](value))
  }

  private case class StoredCommand(kind: String, payload: ujson.Value) derives upickle.default.ReadWriter

  given [T: Fractional: upickle.default.ReadWriter]: upickle.default.ReadWriter[SvgPathBuilderCommand[T]] =
    upickle.default.readwriter[StoredCommand].bimap(
      command => command match {
        case value: AddControlLinesCommand[T] => StoredCommand("AddControlLinesCommand", upickle.default.writeJs(value))
        case value: MoveAbs[T] => StoredCommand("MoveAbs", upickle.default.writeJs(value))
        case value: MoveRel[T] => StoredCommand("MoveRel", upickle.default.writeJs(value))
        case value: ClosePath[T] => StoredCommand("ClosePath", upickle.default.writeJs(value))
        case value: LineAbs[T] => StoredCommand("LineAbs", upickle.default.writeJs(value))
        case value: LineRel[T] => StoredCommand("LineRel", upickle.default.writeJs(value))
        case value: HorizontalRel[T] => StoredCommand("HorizontalRel", upickle.default.writeJs(value))
        case value: VerticalRel[T] => StoredCommand("VerticalRel", upickle.default.writeJs(value))
        case value: CubicAbs[T] => StoredCommand("CubicAbs", upickle.default.writeJs(value))
        case value: CubicRel[T] => StoredCommand("CubicRel", upickle.default.writeJs(value))
        case value: QuadAbs[T] => StoredCommand("QuadAbs", upickle.default.writeJs(value))
        case value: QuadRel[T] => StoredCommand("QuadRel", upickle.default.writeJs(value))
        case value: ArcAbs[T] => StoredCommand("ArcAbs", upickle.default.writeJs(value))
        case value: ArcRel[T] => StoredCommand("ArcRel", upickle.default.writeJs(value))
        case value: CenteredCircleControl[T] => StoredCommand("CenteredCircleControl", upickle.default.writeJs(value))
        case value: RawAppend[T] => StoredCommand("RawAppend", upickle.default.writeJs(value))
        case other => throw new IllegalArgumentException(s"Unsupported SVG command: ${other.getClass.getName}")
      },
      stored => stored.kind match {
        case "AddControlLinesCommand" => upickle.default.read[AddControlLinesCommand[T]](stored.payload)
        case "MoveAbs" => upickle.default.read[MoveAbs[T]](stored.payload)
        case "MoveRel" => upickle.default.read[MoveRel[T]](stored.payload)
        case "ClosePath" => upickle.default.read[ClosePath[T]](stored.payload)
        case "LineAbs" => upickle.default.read[LineAbs[T]](stored.payload)
        case "LineRel" => upickle.default.read[LineRel[T]](stored.payload)
        case "HorizontalRel" => upickle.default.read[HorizontalRel[T]](stored.payload)
        case "VerticalRel" => upickle.default.read[VerticalRel[T]](stored.payload)
        case "CubicAbs" => upickle.default.read[CubicAbs[T]](stored.payload)
        case "CubicRel" => upickle.default.read[CubicRel[T]](stored.payload)
        case "QuadAbs" => upickle.default.read[QuadAbs[T]](stored.payload)
        case "QuadRel" => upickle.default.read[QuadRel[T]](stored.payload)
        case "ArcAbs" => upickle.default.read[ArcAbs[T]](stored.payload)
        case "ArcRel" => upickle.default.read[ArcRel[T]](stored.payload)
        case "CenteredCircleControl" => upickle.default.read[CenteredCircleControl[T]](stored.payload)
        case "RawAppend" => upickle.default.read[RawAppend[T]](stored.payload)
        case other => throw new IllegalArgumentException(s"Unknown SVG command: $other")
      })
}