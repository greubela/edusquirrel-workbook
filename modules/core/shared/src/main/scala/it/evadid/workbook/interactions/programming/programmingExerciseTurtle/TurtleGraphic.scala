package it.evadid.workbook.interactions.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.geometry.{Line, Point}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.SvgToTurtleProgram.convert
import upickle.default.*

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

  private def normal(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0
  private def svgY(turtleY: Double): Double = if turtleY == 0.0 then 0.0 else -turtleY

  /** The turtle and its recorded geometry after some commands. Positions are SVG coordinates (y down, as
    * `forward` draws); commands take turtle coordinates (y up). A turn after a forward movement opens an
    * angle, which the next forward movement from the same vertex closes.
    */
  private final case class TraceState(
      position: Point[Double] = Point(0.0, 0.0),
      heading: Double = 0.0,
      penDown: Boolean = true,
      movements: Vector[Movement] = Vector.empty,
      angles: Vector[Angle] = Vector.empty,
      lastForwardLine: Option[Int] = None,
      pendingAngle: Option[PendingAngle] = None
  ) {
    private def withoutAngleReference: TraceState = copy(lastForwardLine = None, pendingAngle = None)

    def execute(command: TurtleCommand[Double]): TraceState = {
      import TurtleCommand.*
      require(command.args.forall(_.isFinite), "Turtle arguments must be finite")
      command match {
        case Forward(distance) => forward(distance)
        case Backward(distance) => forward(-distance)
        case Left(degrees) => turn(degrees)
        case Right(degrees) => turn(-degrees)
        case Goto(x, y) => move(Point(x, svgY(y)), isForward = false)
        case SetX(x) => move(Point(x, position.y), isForward = false)
        case SetY(y) => move(Point(position.x, svgY(y)), isForward = false)
        case SetHeading(degrees) => copy(heading = normal(degrees)).withoutAngleReference
        case PenUp => copy(penDown = false)
        case PenDown => copy(penDown = true)
        case Home => move(Point(0.0, 0.0), isForward = false).copy(heading = 0.0)
        case Clear => copy(movements = Vector.empty, angles = Vector.empty).withoutAngleReference
        case Reset => TraceState()
        case Circle(radius, extent, _) => arc(radius, extent.getOrElse(360.0), left = true)
        case ArcLeft(radius, extent) => arc(radius, extent, left = true)
        case ArcRight(radius, extent) => arc(radius, extent, left = false)
        case Dot(size) => dot(size)
        case _ => this
      }
    }

    /** Moves with the current pen; a pen-up movement is a jump. */
    private def move(end: Point[Double], isForward: Boolean): TraceState = {
      require(end.x.isFinite && end.y.isFinite, "Turtle coordinates must be finite")
      if end == position then (if isForward then this else withoutAngleReference)
      else {
        val index = movements.size
        val moved = copy(position = end, movements = movements :+ Movement(position, end, !penDown))
        if isForward then {
          val closedAngle = pendingAngle.filter(p => p.vertex == position && math.abs(p.degrees % 360.0) > 1e-7)
            .map(p => Angle(position, p.fromHeading, p.degrees, p.lineBefore, index))
          moved.copy(angles = angles ++ closedAngle, lastForwardLine = Some(index), pendingAngle = None)
        } else moved.withoutAngleReference
      }
    }

    private def forward(distance: Double): TraceState =
      if distance == 0.0 then this
      else {
        val radians = Math.toRadians(heading)
        move(Point(position.x + Math.cos(radians) * distance, position.y - Math.sin(radians) * distance), isForward = true)
      }

    // Preserve the signed rotation across consecutive turns at the same vertex.
    private def turn(degrees: Double): TraceState = {
      val pending = lastForwardLine.fold(pendingAngle) { line =>
        pendingAngle match
          case Some(p) =>
            val accumulated = p.degrees + degrees
            require(accumulated.isFinite, "Accumulated turtle turn must be finite")
            Some(p.copy(degrees = accumulated))
          case None => Some(PendingAngle(position, heading, degrees, line))
      }
      copy(heading = normal(heading + normal(degrees)), pendingAngle = pending)
    }

    // The chords of TurtlePathBuilder's (CPython's) circle, with its half turns.
    private def arc(radius: Double, extent: Double, left: Boolean): TraceState = {
      val (steps, distance, rotation) = TurtlePathBuilder.arcSteps(radius, extent, left)
      (0 until steps).foldLeft(turn(rotation / 2))((state, _) => state.forward(distance).turn(rotation)).turn(-rotation / 2)
    }

    // A dot is drawn even with the pen up and leaves position/heading unchanged.
    // Use the existing SVG conversion for its circular outline rather than a
    // second arc implementation. Style/fill are outside segment grading.
    private def dot(size: Double): TraceState = {
      val path = TurtlePathBuilder[Double]().goto(position.x, -position.y).clear().dot(size)
        .svgPathBuilder.furtherCommands.map(_.getPathDString()).mkString(" ")
      val (outline, _) = trace(convert(s"M ${position.x} ${position.y} $path"))
      copy(movements = movements ++ outline.filterNot(_.jump)).withoutAngleReference
    }
  }

  private def trace(program: Seq[TurtleCommand[Double]]): (Seq[Movement], Seq[Angle]) = {
    val traced = program.foldLeft(TraceState())(_.execute(_))
    (traced.movements.toList, traced.angles.toList)
  }

  /** A turtle graphic represented directly by an SVG path D string.
   * The SVG path is converted to turtle commands when needed.
   */
  case class TurtleGraphicSvgString(svgPathDString: String) extends TurtleGraphic derives upickle.default.ReadWriter {
    lazy val toTurtleProgram: Seq[TurtleCommand[Double]] = convert(svgPathDString)

    lazy val toSvgPathDString: String = svgPathDString
  }

  /** A turtle graphic represented directly as a list of turtle commands.
   */
  case class TurtleGraphicProgram(program: Seq[TurtleCommand[Double]]) extends TurtleGraphic derives upickle.default.ReadWriter {
    lazy val toTurtleProgram: Seq[TurtleCommand[Double]] = program

    lazy val toSvgPathDString: String = {
      val builder = it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder[Double]()
      val finalBuilder = program.foldLeft(builder) { (tb, cmd) => tb.handleCommand(cmd) }
      finalBuilder.svgPathBuilder.furtherCommands.map(_.getPathDString()).mkString.trim
    }
  }

  /** A turtle graphic represented as a list of lines, with automatic calculation of jumps and rotations.
   * Lines are drawn with the pen down; jumps between lines (pen up/down) are automatically calculated
   * following the same rules as SVG to turtle conversion (no rotation > 180°).
   */
  case class TurtleLineBasedProgram(lines: Seq[Line[Double]]) extends TurtleGraphic derives upickle.default.ReadWriter {
    lazy val toTurtleProgram: Seq[TurtleCommand[Double]] =
      TurtleLineBasedProgram.toTurtleProgram(lines)

    lazy val toSvgPathDString: String = {
      if (lines.isEmpty) ""
      else {
        val start = lines.head.start
        // A line that does not continue the previous one starts a new subpath.
        val previousEnds = start +: lines.map(_.end)
        val segments = lines.zip(previousEnds).flatMap { (line, current) =>
          (if line.start != current then List(s"M ${pointToString(line.start)}") else Nil) :+ s"L ${pointToString(line.end)}"
        }
        (s"M ${pointToString(start)}" +: segments).mkString(" ")
      }
    }

    private def pointToString(p: Point[Double]): String = s"${coordinateToString(p.x)} ${coordinateToString(p.y)}"

    private def coordinateToString(value: Double): String = BigDecimal(value).bigDecimal.stripTrailingZeros.toPlainString
  }

  object TurtleLineBasedProgram {
    /** Draws each line with the pen down, travelling with the pen up wherever a line does not continue the previous one. */
    private def toTurtleProgram(lines: Seq[Line[Double]]): Seq[TurtleCommand[Double]] = {
      val (commands, _, _) = lines.foldLeft((Vector.empty[TurtleCommand[Double]], Point(0.0, 0.0), 0.0)) {
        case ((drawn, current, heading), line) =>
          val (travel, startHeading) =
            if current == line.start then (Vector.empty, heading)
            else {
              val (path, pathHeading) = straightTo(current, line.start, heading)
              ((TurtleCommand.PenUp +: path) :+ TurtleCommand.PenDown, pathHeading)
            }
          val (draw, endHeading) = straightTo(line.start, line.end, startHeading)
          (drawn ++ travel ++ draw, line.end, endHeading)
      }
      (commands :+ TurtleCommand.PenUp).toList
    }

    /** The turn and forward commands from `start` to `end`, and the heading afterwards; none for a negligible distance. */
    private def straightTo(start: Point[Double], end: Point[Double], heading: Double): (Vector[TurtleCommand[Double]], Double) = {
      val dx = end.x - start.x
      val dy = end.y - start.y
      val distance = math.hypot(dx, dy)
      if (distance > 1e-12) {
        val target = math.toDegrees(math.atan2(-dy, dx))
        val delta = ((target - heading + 540) % 360) - 180
        val rotation: Vector[TurtleCommand[Double]] =
          if (delta > 1e-10) Vector(TurtleCommand.Left(delta))
          else if (delta < -1e-10) Vector(TurtleCommand.Right(-delta))
          else Vector.empty
        (rotation :+ TurtleCommand.Forward(distance), target)
      } else (Vector.empty, heading)
    }
  }

}
