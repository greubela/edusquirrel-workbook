package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand

import scala.annotation.tailrec

/** Converts an SVG path `d` value to the small, portable turtle instruction set.
  *
  * SVG curves cannot be represented exactly by a turtle which only knows straight
  * lines. Bezier curves therefore visit their control points and elliptical arcs
  * are split into line segments of at most ten degrees.
  */
final class SvgToTurtleProgram {
  import SvgToTurtleProgram.*

  def transform(pathData: String): Seq[TurtleCommand[Double]] = convert(pathData)
  def apply(pathData: String): Seq[TurtleCommand[Double]] = transform(pathData)
}

object SvgToTurtleProgram {
  private final case class Point(x: Double, y: Double) {
    def +(other: Point): Point = Point(x + other.x, y + other.y)
    def -(other: Point): Point = Point(x - other.x, y - other.y)
  }

  private val Token = "([AaCcHhLlMmQqSsTtVvZz])|([-+]?(?:(?:\\d+(?:\\.\\d*)?)|(?:\\.\\d+))(?:[eE][-+]?\\d+)?)".r

  def apply(pathData: String): Seq[TurtleCommand[Double]] = convert(pathData)

  def convert(pathData: String): Seq[TurtleCommand[Double]] = {
    val matches = Token.findAllMatchIn(pathData).toList
    val ignored = Token.replaceAllIn(pathData, "").replaceAll("[\\s,]", "")
    require(ignored.isEmpty, s"Invalid SVG path data near '$ignored'")
    val tokens = matches.map(_.matched)
    val points = parse(tokens)
    turtleCommands(points)
  }

  /** The parsed destinations so far, with the state that following path commands refer to. */
  private final case class PathCursor(
      current: Point = Point(0, 0),
      subpathStart: Point = Point(0, 0),
      lastCubicControl: Option[Point] = None,
      lastQuadraticControl: Option[Point] = None,
      destinations: Vector[(Point, Boolean)] = Vector.empty
  ) {
    def moveTo(point: Point): PathCursor = PathCursor(point, point, None, None, destinations :+ (point -> false))
    def lineTo(points: Point*): PathCursor =
      copy(current = points.lastOption.getOrElse(current), destinations = destinations ++ points.map(_ -> true))
    def withControls(cubic: Option[Point] = None, quadratic: Option[Point] = None): PathCursor =
      copy(lastCubicControl = cubic, lastQuadraticControl = quadratic)
    def withoutControls: PathCursor = withControls()
    /** The previous control point mirrored through the current point, as smooth curves continue a curve. */
    def reflected(control: Option[Point]): Point = control.map(c => current + (current - c)).getOrElse(current)
  }

  /** Each entry is a destination and whether the trip to it draws ink. */
  private def parse(pathTokens: Seq[String]): Seq[(Point, Boolean)] = {
    val tokens = pathTokens.toVector
    def isCommand(s: String) = s.length == 1 && s.head.isLetter

    @tailrec
    def parseFrom(index: Int, previousCommand: Char, cursor: PathCursor): Seq[(Point, Boolean)] =
      if index >= tokens.length then cursor.destinations.toList
      else {
        val explicit = isCommand(tokens(index))
        if !explicit then require(previousCommand != ' ', "SVG path must begin with a command")
        val command = if explicit then tokens(index).head else previousCommand
        val start = if explicit then index + 1 else index
        val relative = command.isLower
        val origin = cursor.current
        /** The command's next `count` parameters, which must all be present. */
        def numbers(count: Int, message: String): Vector[Double] = {
          require(start + count <= tokens.length && (0 until count).forall(k => !isCommand(tokens(start + k))), message)
          tokens.slice(start, start + count).map(_.toDouble)
        }
        def point(x: Double, y: Double): Point = if relative then origin + Point(x, y) else Point(x, y)
        command.toUpper match
          case 'M' =>
            val v = numbers(2, "Move command requires a coordinate pair")
            // Subsequent pairs after moveto are implicit lineto commands.
            parseFrom(start + 2, if relative then 'l' else 'L', cursor.moveTo(point(v(0), v(1))))
          case 'L' =>
            val v = numbers(2, "Line command requires a coordinate pair")
            parseFrom(start + 2, command, cursor.lineTo(point(v(0), v(1))).withoutControls)
          case 'H' =>
            val x = numbers(1, "Horizontal line requires a coordinate")(0)
            parseFrom(start + 1, command, cursor.lineTo(Point(if relative then origin.x + x else x, origin.y)).withoutControls)
          case 'V' =>
            val y = numbers(1, "Vertical line requires a coordinate")(0)
            parseFrom(start + 1, command, cursor.lineTo(Point(origin.x, if relative then origin.y + y else y)).withoutControls)
          case 'C' =>
            val v = numbers(6, "Cubic curve requires three coordinate pairs")
            val c2 = point(v(2), v(3))
            parseFrom(start + 6, command, cursor.lineTo(point(v(0), v(1)), c2, point(v(4), v(5))).withControls(cubic = Some(c2)))
          case 'S' =>
            val v = numbers(4, "Smooth cubic curve requires two coordinate pairs")
            val c2 = point(v(0), v(1))
            parseFrom(start + 4, command,
              cursor.lineTo(cursor.reflected(cursor.lastCubicControl), c2, point(v(2), v(3))).withControls(cubic = Some(c2)))
          case 'Q' =>
            val v = numbers(4, "Quadratic curve requires two coordinate pairs")
            val control = point(v(0), v(1))
            parseFrom(start + 4, command, cursor.lineTo(control, point(v(2), v(3))).withControls(quadratic = Some(control)))
          case 'T' =>
            val v = numbers(2, "Smooth quadratic curve requires a coordinate pair")
            val control = cursor.reflected(cursor.lastQuadraticControl)
            parseFrom(start + 2, command, cursor.lineTo(control, point(v(0), v(1))).withControls(quadratic = Some(control)))
          case 'A' =>
            val v = numbers(7, "Arc requires seven parameters")
            val large = flag(v(3)); val sweep = flag(v(4))
            val arc = arcPoints(origin, point(v(5), v(6)), v(0), v(1), v(2), large, sweep)
            parseFrom(start + 7, command, cursor.lineTo(arc*).withoutControls)
          case 'Z' =>
            parseFrom(start, ' ', cursor.lineTo(cursor.subpathStart).withoutControls)
          case other => throw new IllegalArgumentException(s"Unsupported SVG path command: $other")
      }

    parseFrom(0, ' ', PathCursor())
  }

  private def flag(value: Double): Boolean = {
    require(value == 0 || value == 1, s"SVG arc flag must be 0 or 1, not $value")
    value == 1
  }

  private def arcPoints(start: Point, end: Point, rxInput: Double, ryInput: Double,
                        rotationDegrees: Double, largeArc: Boolean, sweep: Boolean): Seq[Point] = {
    val radiusX = math.abs(rxInput); val radiusY = math.abs(ryInput)
    if start == end then Nil
    else if radiusX == 0 || radiusY == 0 then List(end)
    else {
      val phi = math.toRadians(rotationDegrees % 360); val cosPhi = math.cos(phi); val sinPhi = math.sin(phi)
      val dx = (start.x - end.x) / 2; val dy = (start.y - end.y) / 2
      val x1 = cosPhi * dx + sinPhi * dy; val y1 = -sinPhi * dx + cosPhi * dy
      // Radii too small to reach the end point are scaled up uniformly.
      val scale = x1 * x1 / (radiusX * radiusX) + y1 * y1 / (radiusY * radiusY)
      val (rx, ry) = if scale > 1 then { val s = math.sqrt(scale); (radiusX * s, radiusY * s) } else (radiusX, radiusY)
      val numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
      val denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1
      val sign = if largeArc == sweep then -1.0 else 1.0
      val factor = sign * math.sqrt(math.max(0, numerator / denominator))
      val cx1 = factor * rx * y1 / ry; val cy1 = factor * -ry * x1 / rx
      val cx = cosPhi * cx1 - sinPhi * cy1 + (start.x + end.x) / 2
      val cy = sinPhi * cx1 + cosPhi * cy1 + (start.y + end.y) / 2
      def angle(ux: Double, uy: Double, vx: Double, vy: Double) = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
      val ux = (x1 - cx1) / rx; val uy = (y1 - cy1) / ry
      val vx = (-x1 - cx1) / rx; val vy = (-y1 - cy1) / ry
      val startAngle = angle(1, 0, ux, uy)
      val unsignedDelta = angle(ux, uy, vx, vy)
      val delta =
        if !sweep && unsignedDelta > 0 then unsignedDelta - 2 * math.Pi
        else if sweep && unsignedDelta < 0 then unsignedDelta + 2 * math.Pi
        else unsignedDelta
      val segments = math.max(1, math.ceil(math.abs(delta) / math.toRadians(10)).toInt)
      (1 to segments).map { n =>
        if n == segments then end
        else { val theta = startAngle + delta * n / segments; val x = rx * math.cos(theta); val y = ry * math.sin(theta)
          Point(cx + cosPhi * x - sinPhi * y, cy + sinPhi * x + cosPhi * y) }
      }.toList
    }
  }

  private def turtleCommands(destinations: Seq[(Point, Boolean)]): Seq[TurtleCommand[Double]] =
    destinations.foldLeft((Vector.empty[TurtleCommand[Double]], Point(0, 0), 0.0, true)) {
      case ((commands, current, heading, penDown), (next, draw)) =>
        val pen: Vector[TurtleCommand[Double]] =
          if draw == penDown then Vector.empty
          else Vector(if draw then TurtleCommand.PenDown else TurtleCommand.PenUp)
        val dx = next.x - current.x; val dy = next.y - current.y
        val distance = math.hypot(dx, dy)
        val (movement, nextHeading) =
          if distance > 1e-12 then {
            val target = math.toDegrees(math.atan2(-dy, dx))
            val delta = ((target - heading + 540) % 360) - 180
            val rotation: Vector[TurtleCommand[Double]] =
              if delta > 1e-10 then Vector(TurtleCommand.Left(delta))
              else if delta < -1e-10 then Vector(TurtleCommand.Right(-delta))
              else Vector.empty
            (rotation :+ TurtleCommand.Forward(distance), target)
          } else (Vector.empty, heading)
        (commands ++ pen ++ movement, next, nextHeading, draw)
    }._1.toList

}
