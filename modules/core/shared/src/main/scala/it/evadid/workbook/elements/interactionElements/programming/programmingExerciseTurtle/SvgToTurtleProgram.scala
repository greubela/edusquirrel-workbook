package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand

import scala.collection.mutable.ListBuffer

/** Converts an SVG path `d` value to the small, portable turtle instruction set.
  *
  * SVG curves cannot be represented exactly by a turtle which only knows straight
  * lines. Bezier curves therefore visit their control points and elliptical arcs
  * are split into line segments of at most ten degrees.
  */
final class SvgToTurtleProgram {
  import SvgToTurtleProgram.*

  def transform(pathData: String): List[TurtleCommand[Double]] = convert(pathData)
  def apply(pathData: String): List[TurtleCommand[Double]] = transform(pathData)
}

object SvgToTurtleProgram {
  private final case class Point(x: Double, y: Double) derives upickle.default.ReadWriter {
    def +(other: Point): Point = Point(x + other.x, y + other.y)
    def -(other: Point): Point = Point(x - other.x, y - other.y)
  }

  private val Token = "([AaCcHhLlMmQqSsTtVvZz])|([-+]?(?:(?:\\d+(?:\\.\\d*)?)|(?:\\.\\d+))(?:[eE][-+]?\\d+)?)".r

  def apply(pathData: String): List[TurtleCommand[Double]] = convert(pathData)

  def convert(pathData: String): List[TurtleCommand[Double]] = {
    val matches = Token.findAllMatchIn(pathData).toList
    val ignored = Token.replaceAllIn(pathData, "").replaceAll("[\\s,]", "")
    require(ignored.isEmpty, s"Invalid SVG path data near '$ignored'")
    val tokens = matches.map(_.matched)
    val points = parse(tokens)
    turtleCommands(points)
  }

  /** Each entry is a destination and whether the trip to it draws ink. */
  private def parse(tokens: List[String]): List[(Point, Boolean)] = {
    val out = ListBuffer.empty[(Point, Boolean)]
    var i = 0
    var command = ' '
    var current = Point(0, 0)
    var subpathStart = current
    var lastCubicControl: Option[Point] = None
    var lastQuadraticControl: Option[Point] = None

    def isCommand(s: String) = s.length == 1 && s.head.isLetter
    def available(n: Int) = i + n <= tokens.length && (0 until n).forall(k => !isCommand(tokens(i + k)))
    def number(): Double = { val value = tokens(i).toDouble; i += 1; value }
    def point(relative: Boolean): Point = {
      val p = Point(number(), number())
      if relative then current + p else p
    }
    def line(p: Point, draw: Boolean = true): Unit = { out += p -> draw; current = p }
    def resetControls(): Unit = { lastCubicControl = None; lastQuadraticControl = None }

    while i < tokens.length do
      if isCommand(tokens(i)) then { command = tokens(i).head; i += 1 }
      else require(command != ' ', "SVG path must begin with a command")
      val relative = command.isLower
      command.toUpper match
        case 'M' =>
          require(available(2), "Move command requires a coordinate pair")
          line(point(relative), draw = false); subpathStart = current; resetControls()
          // Subsequent pairs after moveto are implicit lineto commands.
          command = if relative then 'l' else 'L'
        case 'L' => require(available(2), "Line command requires a coordinate pair"); line(point(relative)); resetControls()
        case 'H' =>
          require(available(1), "Horizontal line requires a coordinate")
          val x = number(); line(Point(if relative then current.x + x else x, current.y)); resetControls()
        case 'V' =>
          require(available(1), "Vertical line requires a coordinate")
          val y = number(); line(Point(current.x, if relative then current.y + y else y)); resetControls()
        case 'C' =>
          require(available(6), "Cubic curve requires three coordinate pairs")
          val origin = current
          val c1raw = Point(number(), number()); val c2raw = Point(number(), number()); val eraw = Point(number(), number())
          val c1 = if relative then origin + c1raw else c1raw
          val c2 = if relative then origin + c2raw else c2raw
          val end = if relative then origin + eraw else eraw
          line(c1); line(c2); line(end); lastCubicControl = Some(c2); lastQuadraticControl = None
        case 'S' =>
          require(available(4), "Smooth cubic curve requires two coordinate pairs")
          val origin = current
          val c1 = lastCubicControl.map(c => origin + (origin - c)).getOrElse(origin)
          val c2raw = Point(number(), number()); val eraw = Point(number(), number())
          val c2 = if relative then origin + c2raw else c2raw; val end = if relative then origin + eraw else eraw
          line(c1); line(c2); line(end); lastCubicControl = Some(c2); lastQuadraticControl = None
        case 'Q' =>
          require(available(4), "Quadratic curve requires two coordinate pairs")
          val origin = current; val craw = Point(number(), number()); val eraw = Point(number(), number())
          val control = if relative then origin + craw else craw; val end = if relative then origin + eraw else eraw
          line(control); line(end); lastQuadraticControl = Some(control); lastCubicControl = None
        case 'T' =>
          require(available(2), "Smooth quadratic curve requires a coordinate pair")
          val origin = current; val control = lastQuadraticControl.map(c => origin + (origin - c)).getOrElse(origin)
          val end = point(relative); line(control); line(end); lastQuadraticControl = Some(control); lastCubicControl = None
        case 'A' =>
          require(available(7), "Arc requires seven parameters")
          val origin = current; val rx = number(); val ry = number(); val rotation = number()
          val large = flag(number()); val sweep = flag(number()); val rawEnd = Point(number(), number())
          val end = if relative then origin + rawEnd else rawEnd
          arcPoints(origin, end, rx, ry, rotation, large, sweep).foreach(line(_)); resetControls()
        case 'Z' =>
          line(subpathStart); resetControls(); command = ' '
        case other => throw new IllegalArgumentException(s"Unsupported SVG path command: $other")
    out.toList
  }

  private def flag(value: Double): Boolean = {
    require(value == 0 || value == 1, s"SVG arc flag must be 0 or 1, not $value")
    value == 1
  }

  private def arcPoints(start: Point, end: Point, rxInput: Double, ryInput: Double,
                        rotationDegrees: Double, largeArc: Boolean, sweep: Boolean): List[Point] = {
    var rx = math.abs(rxInput); var ry = math.abs(ryInput)
    if rx == 0 || ry == 0 || start == end then return if start == end then Nil else List(end)
    val phi = math.toRadians(rotationDegrees % 360); val cosPhi = math.cos(phi); val sinPhi = math.sin(phi)
    val dx = (start.x - end.x) / 2; val dy = (start.y - end.y) / 2
    val x1 = cosPhi * dx + sinPhi * dy; val y1 = -sinPhi * dx + cosPhi * dy
    val scale = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
    if scale > 1 then { val s = math.sqrt(scale); rx *= s; ry *= s }
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
    var delta = angle(ux, uy, vx, vy)
    if !sweep && delta > 0 then delta -= 2 * math.Pi
    if sweep && delta < 0 then delta += 2 * math.Pi
    val segments = math.max(1, math.ceil(math.abs(delta) / math.toRadians(10)).toInt)
    (1 to segments).map { n =>
      if n == segments then end
      else { val theta = startAngle + delta * n / segments; val x = rx * math.cos(theta); val y = ry * math.sin(theta)
        Point(cx + cosPhi * x - sinPhi * y, cy + sinPhi * x + cosPhi * y) }
    }.toList
  }

  private def turtleCommands(destinations: List[(Point, Boolean)]): List[TurtleCommand[Double]] = {
    val result = ListBuffer.empty[TurtleCommand[Double]]
    var current = Point(0, 0); var heading = 0.0; var penDown = true
    destinations.foreach { (next, draw) =>
      if draw != penDown then { result += TurtleCommand(if draw then "penDown" else "penUp"); penDown = draw }
      val dx = next.x - current.x; val dy = next.y - current.y
      val distance = math.hypot(dx, dy)
      if distance > 1e-12 then
        val target = math.toDegrees(math.atan2(-dy, dx))
        val delta = ((target - heading + 540) % 360) - 180
        if delta > 1e-10 then result += TurtleCommand("turnLeft", List(delta))
        else if delta < -1e-10 then result += TurtleCommand("turnRight", List(-delta))
        result += TurtleCommand("forward", List(distance)); heading = target
      current = next
    }
    result.toList
  }
}
