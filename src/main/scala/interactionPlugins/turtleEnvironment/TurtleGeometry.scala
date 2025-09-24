package interactionPlugins.turtleEnvironment

case class TurtlePoint(x: Double, y: Double) {
  def +(other: TurtlePoint): TurtlePoint = TurtlePoint(x + other.x, y + other.y)
  def -(other: TurtlePoint): TurtlePoint = TurtlePoint(x - other.x, y - other.y)
  def translate(dx: Double, dy: Double): TurtlePoint = TurtlePoint(x + dx, y + dy)
  def scale(factor: Double): TurtlePoint = TurtlePoint(x * factor, y * factor)
  def dot(other: TurtlePoint): Double = x * other.x + y * other.y
  def magnitude: Double = math.hypot(x, y)
  def normalized: TurtlePoint = {
    val mag = magnitude
    if (mag == 0.0) TurtlePoint(0.0, 0.0) else TurtlePoint(x / mag, y / mag)
  }
  def distanceTo(other: TurtlePoint): Double = math.hypot(x - other.x, y - other.y)
}

case class TurtleLineSegment(start: TurtlePoint, end: TurtlePoint) {
  def length: Double = start.distanceTo(end)

  def direction: TurtlePoint = end - start

  def normalizedDirection: TurtlePoint = direction.normalized

  def midpoint: TurtlePoint = TurtlePoint((start.x + end.x) / 2.0, (start.y + end.y) / 2.0)

  def reversed: TurtleLineSegment = TurtleLineSegment(end, start)

  def translate(dx: Double, dy: Double): TurtleLineSegment =
    TurtleLineSegment(start.translate(dx, dy), end.translate(dx, dy))

  def translate(delta: TurtlePoint): TurtleLineSegment = translate(delta.x, delta.y)

  def distanceTo(point: TurtlePoint): Double = {
    val segVector = direction
    val segLengthSquared = segVector.dot(segVector)
    if (segLengthSquared == 0.0) {
      start.distanceTo(point)
    } else {
      val projection = (point - start).dot(segVector) / segLengthSquared
      if (projection <= 0.0) {
        start.distanceTo(point)
      } else if (projection >= 1.0) {
        end.distanceTo(point)
      } else {
        val projectedPoint = start + segVector.scale(projection)
        projectedPoint.distanceTo(point)
      }
    }
  }

  def distanceToSegment(other: TurtleLineSegment): Double = {
    val cross = segmentsCross(other)
    if (cross) {
      0.0
    } else {
      List(
        distanceTo(other.start),
        distanceTo(other.end),
        other.distanceTo(start),
        other.distanceTo(end)
      ).min
    }
  }

  private def segmentsCross(other: TurtleLineSegment): Boolean = {
    val epsilon = 1e-9
    def orientation(p: TurtlePoint, q: TurtlePoint, r: TurtlePoint): Double =
      (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)

    def onSegment(p: TurtlePoint, q: TurtlePoint, r: TurtlePoint): Boolean =
      q.x <= math.max(p.x, r.x) + epsilon &&
        q.x + epsilon >= math.min(p.x, r.x) &&
        q.y <= math.max(p.y, r.y) + epsilon &&
        q.y + epsilon >= math.min(p.y, r.y)

    def isZero(value: Double): Boolean = math.abs(value) <= epsilon

    val o1 = orientation(start, end, other.start)
    val o2 = orientation(start, end, other.end)
    val o3 = orientation(other.start, other.end, start)
    val o4 = orientation(other.start, other.end, end)

    if (isZero(o1) && onSegment(start, other.start, end)) return true
    if (isZero(o2) && onSegment(start, other.end, end)) return true
    if (isZero(o3) && onSegment(other.start, start, other.end)) return true
    if (isZero(o4) && onSegment(other.start, end, other.end)) return true

    (o1 > 0) != (o2 > 0) && (o3 > 0) != (o4 > 0)
  }

  def angleTo(other: TurtleLineSegment): Double = {
    val dot = normalizedDirection.dot(other.normalizedDirection)
    val clamped = math.max(-1.0, math.min(1.0, dot))
    math.toDegrees(math.acos(math.abs(clamped)))
  }

  def approximatelyEquals(other: TurtleLineSegment, tolerance: Double = 0.1): Boolean = {
    def eq(a: TurtlePoint, b: TurtlePoint) = math.abs(a.x - b.x) <= tolerance && math.abs(a.y - b.y) <= tolerance
    (eq(start, other.start) && eq(end, other.end)) || (eq(start, other.end) && eq(end, other.start))
  }
}

case class ColoredTurtleLine(segment: TurtleLineSegment, color: String)

case class TurtleBounds(minX: Double, minY: Double, maxX: Double, maxY: Double) {
  def width: Double = math.max(1.0, maxX - minX)
  def height: Double = math.max(1.0, maxY - minY)

  def expand(margin: Double): TurtleBounds =
    TurtleBounds(minX - margin, minY - margin, maxX + margin, maxY + margin)
}

case class TurtleView(width: Double, height: Double, offset: TurtlePoint)

object TurtleGeometry {

  val DefaultMargin: Double = 20.0

  def computeBounds(segments: List[TurtleLineSegment]): Option[TurtleBounds] =
    if (segments.isEmpty) None
    else {
      val xs = segments.flatMap(segment => List(segment.start.x, segment.end.x))
      val ys = segments.flatMap(segment => List(segment.start.y, segment.end.y))
      Some(TurtleBounds(xs.min, ys.min, xs.max, ys.max))
    }

  def viewForSegments(segments: List[TurtleLineSegment], margin: Double = DefaultMargin): TurtleView = {
    computeBounds(segments) match {
      case Some(bounds) =>
        val expanded = bounds.expand(margin)
        TurtleView(expanded.width, expanded.height, TurtlePoint(expanded.minX, expanded.minY))
      case None =>
        TurtleView(200.0, 200.0, TurtlePoint(0.0, 0.0))
    }
  }

  def translate(segment: TurtleLineSegment, delta: TurtlePoint): TurtleLineSegment =
    TurtleLineSegment(segment.start - delta, segment.end - delta)

  def normalizeForView(
    segments: List[TurtleLineSegment],
    margin: Double = DefaultMargin
  ): (List[TurtleLineSegment], TurtleView) = {
    val view = viewForSegments(segments, margin)
    val normalized = segments.map(segment => translate(segment, view.offset))
    (normalized, view)
  }
}
