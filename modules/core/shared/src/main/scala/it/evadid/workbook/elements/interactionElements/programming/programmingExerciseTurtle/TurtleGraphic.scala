package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.geometry.{Line, Point}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.SvgToTurtleProgram.convert
import upickle.default.*

import scala.collection.mutable.ListBuffer
/** A sealed trait representing turtle graphics that can be converted to turtle commands or SVG path data. */
sealed trait TurtleGraphic derives ReadWriter{
  def toTurtleProgram: Seq[TurtleCommand[Double]]

  def toSvgPathDString: String

  /** Ordered movements in SVG coordinates, including pen-up travel. Angle indices
    * refer to this sequence, so they remain stable when jumps are hidden from grading.
    */
  private lazy val trace = TurtleGraphic.trace(toTurtleProgram)
  lazy val renderMovements: Seq[TurtleGraphic.Movement] = trace._1
  lazy val renderLines: Seq[Line[Double]] = renderMovements.filterNot(_.jump).map(_.line)
  lazy val renderAngles: Seq[TurtleGraphic.Angle] = trace._2


}

object TurtleGraphic {

  type Line[T] = it.evadid.core.datastructures.geometry.Line[T]
  val Line: it.evadid.core.datastructures.geometry.Line.type = it.evadid.core.datastructures.geometry.Line

  def apply[T: Fractional](lines: Seq[TurtleCommand[T]]): TurtleGraphic = {
    val res : Seq[TurtleCommand[Double]] = lines.map(_.asDouble)
    TurtleGraphicProgram(res)
  }

  case class Movement(start: Point[Double], end: Point[Double], jump: Boolean = false) derives ReadWriter {
    def line: Line[Double] = Line(start, end)
  }
  /** Signed turtle turn, with references to the adjacent nonzero movements. */
  case class Angle(vertex: Point[Double], fromHeading: Double, degrees: Double, lineBefore: Int, lineAfter: Int) derives ReadWriter
  private case class PendingAngle(vertex: Point[Double], fromHeading: Double, degrees: Double, lineBefore: Int)

  private def trace(program: Seq[TurtleCommand[Double]]): (List[Movement], List[Angle]) = {
    var position = Point(0.0, 0.0)
    var heading = 0.0
    var penDown = true
    val movements = ListBuffer.empty[Movement]
    val angles = ListBuffer.empty[Angle]
    var lastForwardLine: Option[Int] = None
    var pendingAngle: Option[PendingAngle] = None

    def normal(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

    def move(end: Point[Double], jump: Boolean, isForward: Boolean): Unit =
      require(end.x.isFinite && end.y.isFinite, "Turtle coordinates must be finite")
      if !isForward then
        pendingAngle = None
        lastForwardLine = None
      if end == position then return
      val index = movements.size
      movements += Movement(position, end, jump)
      if isForward then
        pendingAngle.filter(p => p.vertex == position && math.abs(p.degrees % 360.0) > 1e-7).foreach { p =>
          angles += Angle(position, p.fromHeading, p.degrees, p.lineBefore, index)
        }
        pendingAngle = None
        lastForwardLine = Some(index)
      else
        pendingAngle = None
        lastForwardLine = None
      position = end

    def forward(distance: Double): Unit =
      val radians = Math.toRadians(heading)
      if distance != 0.0 then
        move(Point(position.x + Math.cos(radians) * distance, position.y - Math.sin(radians) * distance), !penDown, true)

    // Preserve the signed rotation across consecutive turns at the same vertex.
    def turn(degrees: Double): Unit =
      lastForwardLine.foreach { i =>
        pendingAngle = pendingAngle match
          case Some(p) =>
            val accumulated = p.degrees + degrees
            require(accumulated.isFinite, "Accumulated turtle turn must be finite")
            Some(p.copy(degrees = accumulated))
          case None => Some(PendingAngle(position, heading, degrees, i))
      }
      heading = normal(heading + normal(degrees))

    // Match TurtlePathBuilder's polygonal circle/arc approximation.
    def arc(radius: Double, extent: Double, left: Boolean): Unit =
      val stepCount = math.max(8L, math.round(math.abs(extent) / 10.0))
      require(stepCount <= 10000, "Turtle arc has too many steps")
      val steps = stepCount.toInt
      val distance = (2 * math.Pi * radius) * (extent / 360.0) / steps
      val rotation = extent / steps * (if left then 1.0 else -1.0)
      (0 until steps).foreach { _ => forward(distance); turn(rotation) }

    // A dot is drawn even with the pen up and leaves position/heading unchanged.
    // Use the existing SVG conversion for its circular outline rather than a
    // second arc implementation. Style/fill are outside segment grading.
    def dot(size: Double): Unit =
      val path = TurtlePathBuilder[Double]().goto(position.x, position.y).clear().dot(size)
        .svgPathBuilder.furtherCommands.map(_.getPathDString()).mkString(" ")
      val (outline, _) = trace(convert(s"M ${position.x} ${position.y} $path"))
      movements ++= outline.filterNot(_.jump)
      pendingAngle = None
      lastForwardLine = None

    program.foreach { command =>
      val name = command.name.trim.toLowerCase.replace('-', '_')
      val args = command.args
      require(args.forall(_.isFinite), "Turtle arguments must be finite")
      name match
        case "forward" | "fd" => args.headOption.foreach(forward)
        case "backward" | "back" | "bk" => args.headOption.foreach(d => forward(-d))
        case "left" | "lt" | "turn_left" | "turnleft" => args.headOption.foreach { degrees =>
          turn(degrees)
        }
        case "right" | "rt" | "turn" | "turn_right" | "turnright" => args.headOption.foreach { degrees =>
          turn(-degrees)
        }
        case "goto" | "setpos" | "setposition" | "goto_x_y" | "gotoxy" if args.size >= 2 =>
          move(Point(args(0), args(1)), jump = !penDown, isForward = false)
        case "setx" | "set_x" | "setxposition" => args.headOption.foreach(x => move(Point(x, position.y), jump = !penDown, isForward = false))
        case "sety" | "set_y" | "setyposition" => args.headOption.foreach(y => move(Point(position.x, y), jump = !penDown, isForward = false))
        case "setheading" | "seth" | "set_heading" => args.headOption.foreach { h =>
          heading = normal(h)
          pendingAngle = None
          lastForwardLine = None
        }
        case "penup" | "pu" | "up" | "pen_up" => penDown = false
        case "pendown" | "pd" | "down" | "pen_down" => penDown = true
        case "home" => move(Point(0.0, 0.0), jump = !penDown, isForward = false); heading = 0.0
        case "clear" | "clearscreen" => movements.clear(); angles.clear(); lastForwardLine = None; pendingAngle = None
        case "reset" => movements.clear(); angles.clear(); position = Point(0.0, 0.0); heading = 0.0; penDown = true; lastForwardLine = None; pendingAngle = None
        case "circle" if args.nonEmpty => arc(args.head, args.lift(1).getOrElse(360.0), left = true)
        case "arc" | "arcleft" | "arc_left" if args.size >= 2 => arc(args(0), args(1), left = true)
        case "arcright" | "arc_right" if args.size >= 2 => arc(args(0), args(1), left = false)
        case "dot" => args.headOption.foreach(dot)
        case _ => ()
    }

    (movements.toList, angles.toList)
  }



  /** A turtle graphic represented directly by an SVG path D string.
   * The SVG path is converted to turtle commands when needed.
   */
  case class TurtleGraphicSvgString(svgPathDString: String) extends TurtleGraphic {
    lazy val toTurtleProgram: Seq[TurtleCommand[Double]] = convert(svgPathDString)

    lazy val toSvgPathDString: String = svgPathDString
  }

  /** A turtle graphic represented directly as a list of turtle commands.
   */
  case class TurtleGraphicProgram(program: Seq[TurtleCommand[Double]]) extends TurtleGraphic {
    lazy val toTurtleProgram: Seq[TurtleCommand[Double]] = program

    lazy val toSvgPathDString: String = {
      val builder = it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder[Double]()
      val finalBuilder = program.foldLeft(builder) { (tb, cmd) => tb.handleStringCommand(cmd) }
      finalBuilder.svgPathBuilder.furtherCommands.map(_.getPathDString()).mkString.trim
    }
  }

  /** A turtle graphic represented as a list of lines, with automatic calculation of jumps and rotations.
   * Lines are drawn with the pen down; jumps between lines (pen up/down) are automatically calculated
   * following the same rules as SVG to turtle conversion (no rotation > 180°).
   */
  case class TurtleLineBasedProgram(lines: List[Line[Double]]) extends TurtleGraphic {
    lazy val toTurtleProgram: Seq[TurtleCommand[Double]] =
      TurtleLineBasedProgram.toTurtleProgram(lines)

    lazy val toSvgPathDString: String = {
      if (lines.isEmpty) ""
      else {
        val start = lines.head.start
        val pathParts = List.newBuilder[String]
        pathParts += s"M ${pointToString(start)}"

        var current = start
        lines.foreach { line =>
          if line.start != current then pathParts += s"M ${pointToString(line.start)}"
          pathParts += s"L ${pointToString(line.end)}"
          current = line.end
        }

        pathParts.result().mkString(" ")
      }
    }

    private def pointToString(p: Point[Double]): String = {
      val xStr = BigDecimal(p.x).bigDecimal.stripTrailingZeros.toPlainString
      val yStr = BigDecimal(p.y).bigDecimal.stripTrailingZeros.toPlainString
      s"$xStr $yStr"
    }
  }

  object TurtleLineBasedProgram {
    private def toTurtleProgram(lines: List[Line[Double]]): Seq[TurtleCommand[Double]] = {
      val result = ListBuffer.empty[TurtleCommand[Double]]
      var current = Point(0.0, 0.0)
      var heading = 0.0
      var penDown = true

      lines.foreach { line =>
        val start = line.start
        val end = line.end

        // If we're not at the start of this line, we need to move there (pen up)
        if (current != start) {
          if (penDown) {
            result += TurtleCommand("penUp")
            penDown = false
          }
          val dx = start.x - current.x
          val dy = start.y - current.y
          val distance = math.hypot(dx, dy)
          if (distance > 1e-12) {
            val target = math.toDegrees(math.atan2(-dy, dx))
            val delta = ((target - heading + 540) % 360) - 180
            if (delta > 1e-10) result += TurtleCommand("turnLeft", List(delta))
            else if (delta < -1e-10) result += TurtleCommand("turnRight", List(-delta))
            result += TurtleCommand("forward", List(distance))
            heading = target
          }
        }

        // Draw the line (pen down)
        if (!penDown) {
          result += TurtleCommand("penDown")
          penDown = true
        }

        val dx = end.x - start.x
        val dy = end.y - start.y
        val distance = math.hypot(dx, dy)
        if (distance > 1e-12) {
          val target = math.toDegrees(math.atan2(-dy, dx))
          val delta = ((target - heading + 540) % 360) - 180
          if (delta > 1e-10) result += TurtleCommand("turnLeft", List(delta))
          else if (delta < -1e-10) result += TurtleCommand("turnRight", List(-delta))
          result += TurtleCommand("forward", List(distance))
          heading = target
        }

        current = end
      }

      if (penDown) result += TurtleCommand("penUp")
      result.toList
    }
  }




}
