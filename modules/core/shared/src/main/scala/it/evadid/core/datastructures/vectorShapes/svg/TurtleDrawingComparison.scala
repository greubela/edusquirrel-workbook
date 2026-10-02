package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderCommand.*

/**
 * Geometric comparison of two turtle drawings.
 *
 * Samples points along each drawn segment and checks, in both directions, that
 * every sample lies within a tolerance of the other drawing. Pen-up moves,
 * stroke color/size, dots and stitch modes are ignored. Segment order, drawing
 * direction and how finely a curve is subdivided do not matter.
 */
object TurtleDrawingComparison {

  final case class Segment(from: Point[Double], to: Point[Double]) {
    def length: Double = math.hypot(to.x - from.x, to.y - from.y)
  }

  final case class TurtleComparisonResult(
      matches: Boolean,
      missing: List[Segment],
      extra: List[Segment],
      maxDeviation: Double,
      tolerance: Double
  )

  /** Floor so tiny drawings are not judged on sub-pixel noise. */
  val MinTolerance: Double = 2.0

  /** Fraction of the target bounding-box diagonal used when no tolerance is given. */
  val RelativeTolerance: Double = 0.03

  def compare(
      target: TurtlePathBuilder[Double],
      actual: TurtlePathBuilder[Double],
      tolerance: Option[Double] = None
  ): TurtleComparisonResult = {
    val targetSegs = extractSegments(target)
    val actualSegs = extractSegments(actual)
    val effective = tolerance.getOrElse(defaultTolerance(targetSegs))
    coverage(targetSegs, actualSegs, effective)
  }

  def extractSegments(builder: TurtlePathBuilder[Double]): List[Segment] =
    builder.completedStyledSegments.flatMap(segment => extractFromPath(segment.pathBuilder))

  private def extractFromPath(path: SvgPathBuilderImmutable[Double]): List[Segment] = {
    var current = path.absStartPoint
    val out = scala.collection.mutable.ListBuffer.empty[Segment]
    path.furtherCommands.foreach {
      case MoveAbs(p) =>
        current = p
      case LineAbs(p) =>
        if distance(current, p) > 1e-9 then out += Segment(current, p)
        current = p
      case other =>
        other.toAbsoluteCommand(current) match
          case MoveAbs(p) =>
            current = p
          case LineAbs(p) =>
            if distance(current, p) > 1e-9 then out += Segment(current, p)
            current = p
          case abs =>
            current = abs.positionAfterCommand
    }
    out.toList
  }

  def defaultTolerance(segments: List[Segment]): Double =
    math.max(MinTolerance, RelativeTolerance * diagonal(segments))

  private def diagonal(segments: List[Segment]): Double = {
    val points = segments.flatMap(seg => List(seg.from, seg.to))
    if points.isEmpty then 0.0
    else
      val minX = points.map(_.x).min
      val maxX = points.map(_.x).max
      val minY = points.map(_.y).min
      val maxY = points.map(_.y).max
      math.hypot(maxX - minX, maxY - minY)
  }

  private def coverage(
      target: List[Segment],
      actual: List[Segment],
      tolerance: Double
  ): TurtleComparisonResult = {
    if target.isEmpty && actual.isEmpty then
      TurtleComparisonResult(matches = true, Nil, Nil, maxDeviation = 0.0, tolerance)
    else
      val (missing, targetDev) = uncovered(target, actual, tolerance)
      val (extra, actualDev) = uncovered(actual, target, tolerance)
      val maxDeviation = finiteMax(targetDev, actualDev)
      TurtleComparisonResult(
        matches = missing.isEmpty && extra.isEmpty,
        missing = missing,
        extra = extra,
        maxDeviation = maxDeviation,
        tolerance = tolerance
      )
  }

  /** Segments that have at least one sample farther than `tolerance` from `against`. */
  private def uncovered(
      segments: List[Segment],
      against: List[Segment],
      tolerance: Double
  ): (List[Segment], Double) = {
    val step = math.max(tolerance / 2.0, 1e-6)
    var maxDeviation = 0.0
    val bad = segments.filter { segment =>
      val worst = sample(segment, step).map(point => minDistance(point, against)).max
      if worst > maxDeviation then maxDeviation = worst
      worst > tolerance
    }
    (bad, maxDeviation)
  }

  private def sample(segment: Segment, step: Double): List[Point[Double]] = {
    val length = segment.length
    val pieces = math.max(1, math.ceil(length / step).toInt)
    (0 to pieces).map { index =>
      val t = index.toDouble / pieces
      Point(
        segment.from.x + (segment.to.x - segment.from.x) * t,
        segment.from.y + (segment.to.y - segment.from.y) * t
      )
    }.toList
  }

  private def minDistance(point: Point[Double], segments: List[Segment]): Double =
    if segments.isEmpty then Double.PositiveInfinity
    else segments.map(segment => pointToSegment(point, segment)).min

  private def pointToSegment(point: Point[Double], segment: Segment): Double = {
    val dx = segment.to.x - segment.from.x
    val dy = segment.to.y - segment.from.y
    val len2 = dx * dx + dy * dy
    if len2 < 1e-18 then distance(point, segment.from)
    else
      val raw = ((point.x - segment.from.x) * dx + (point.y - segment.from.y) * dy) / len2
      val t = math.max(0.0, math.min(1.0, raw))
      distance(point, Point(segment.from.x + t * dx, segment.from.y + t * dy))
  }

  private def finiteMax(a: Double, b: Double): Double =
    (a.isFinite, b.isFinite) match
      case (true, true) => math.max(a, b)
      case (true, false) => a
      case (false, true) => b
      case _ => 0.0

  private def distance(a: Point[Double], b: Point[Double]): Double =
    math.hypot(a.x - b.x, a.y - b.y)
}
