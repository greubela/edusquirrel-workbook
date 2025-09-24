package interactionPlugins.turtleEnvironment

case class TurtlePoint(x: Double, y: Double) {
  def +(other: TurtlePoint): TurtlePoint = TurtlePoint(x + other.x, y + other.y)
  def -(other: TurtlePoint): TurtlePoint = TurtlePoint(x - other.x, y - other.y)
}

case class TurtleLineSegment(start: TurtlePoint, end: TurtlePoint) {
  def length: Double = math.hypot(end.x - start.x, end.y - start.y)

  def approximatelyEquals(other: TurtleLineSegment, tolerance: Double = 0.1): Boolean = {
    def eq(a: TurtlePoint, b: TurtlePoint) = math.abs(a.x - b.x) <= tolerance && math.abs(a.y - b.y) <= tolerance
    (eq(start, other.start) && eq(end, other.end)) || (eq(start, other.end) && eq(end, other.start))
  }
}

case class ColoredTurtleLine(segment: TurtleLineSegment, color: String)
