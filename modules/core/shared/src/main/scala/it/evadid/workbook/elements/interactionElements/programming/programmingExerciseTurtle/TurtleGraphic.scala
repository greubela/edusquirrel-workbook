package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.geometry.{Line, Point}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.SvgToTurtleProgram.convert
import upickle.default.*

import scala.collection.mutable.ListBuffer
/** A sealed trait representing turtle graphics that can be converted to turtle commands or SVG path data. */
sealed trait TurtleGraphic derives ReadWriter{
  def toTurtleProgram: Seq[TurtleCommand[Double]]

  def toSvgPathDString: String

  lazy val renderLines: Seq[Line[Double]] = {
    ???
  }

  lazy val renderAngles: Seq[Line[Double]] = {
    ???
  }


}

object TurtleGraphic {


//  private given ReadWriter[TurtleCommand[Double]] = macroRW

  /** A turtle graphic represented directly by an SVG path D string.
   * The SVG path is converted to turtle commands when needed.
   */
  case class TurtleGraphicSvgString(svgPathDString: String) extends TurtleGraphic {
    lazy val toTurtleProgram: Seq[TurtleCommand[Double]] = convert(svgPathDString)

    lazy val toSvgPathDString: String = svgPathDString
  }

  /** A turtle graphic represented directly as a list of turtle commands.
   */
  case class TurtleGraphicProgram(program: List[TurtleCommand[Double]]) extends TurtleGraphic {
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

        lines.foreach { line =>
          pathParts += s"L ${pointToString(line.end)}"
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
