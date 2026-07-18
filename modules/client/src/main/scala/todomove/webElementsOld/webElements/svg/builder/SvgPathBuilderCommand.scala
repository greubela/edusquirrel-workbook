package todomove.webElementsOld.webElements.svg.builder

import SvgPathBuilderCommand.{AbsoluteCommand, RelativeCommand}
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import todomove.webElementsOld.webElements.svg.atomarElements.AppLineSvgElement

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
      import N.*
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

}