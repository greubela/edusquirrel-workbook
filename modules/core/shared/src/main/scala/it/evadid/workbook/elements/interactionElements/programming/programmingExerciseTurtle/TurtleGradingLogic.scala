package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.geometry.{Line, Point}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic.{Angle, Movement}
import upickle.default.*

/** One-to-one, direction-independent segment grading; traversal order is irrelevant.
 * Stroke subdivision is deliberately significant (two halves are not one segment).
 */
object TurtleGradingLogic {
  enum TurtleLineStatus derives ReadWriter {
    case CORRECT, EXPECTED_BUT_MISSING, EXISTING_BUT_UNEXPECTED
  }

  /** expectedIndex identifies the matched or missing target movement, including duplicates. */
  case class GradingLine(line: Line[Double], status: TurtleLineStatus, jump: Boolean = false,
                         expectedIndex: Option[Int] = None) derives ReadWriter

  case class TurtleGraphicComparison(actual: TurtleGraphic, expected: TurtleGraphic,
                                     tolerance: Double = 1e-7, gradeJumps: Boolean = true) derives upickle.default.ReadWriter {
    require(tolerance.isFinite && tolerance >= 0, "Turtle tolerance must be finite and nonnegative")
    lazy val linesOfActual: Seq[Movement] = actual.renderMovements
    lazy val linesOfExpected: Seq[Movement] = expected.renderMovements
    lazy val difference: List[GradingLine] = compareLines(linesOfActual, linesOfExpected, tolerance, gradeJumps)

    /** Target angles retain correct references even when lines are missing or duplicated. */
    lazy val angles: List[Angle] = {
      val indices = difference.zipWithIndex.flatMap { (line, index) => line.expectedIndex.map(_ -> index) }.toMap
      expected.renderAngles.filter { angle =>
        gradeJumps || (!linesOfExpected(angle.lineBefore).jump && !linesOfExpected(angle.lineAfter).jump)
      }.map { angle =>
        angle.copy(lineBefore = indices(angle.lineBefore), lineAfter = indices(angle.lineAfter))
      }.toList
    }
  }

  def compareLines(actual: Seq[Movement], expected: Seq[Movement], tolerance: Double = 1e-7,
                   gradeJumps: Boolean = true): List[GradingLine] = {
    require(tolerance.isFinite && tolerance >= 0, "Turtle tolerance must be finite and nonnegative")
    require((actual.iterator ++ expected.iterator).forall { movement =>
      List(movement.start.x, movement.start.y, movement.end.x, movement.end.y).forall(_.isFinite)
    }, "Turtle comparison coordinates must be finite")
    val unmatched = scala.collection.mutable.ListBuffer.from(expected.zipWithIndex.filter { (line, _) => gradeJumps || !line.jump })
    val existing = actual.map { movement =>
      if movement.jump && !gradeJumps then GradingLine(movement.line, TurtleLineStatus.CORRECT, jump = true)
      else {
        val index = unmatched.indexWhere { (target, _) =>
          movement.jump == target.jump && sameLine(movement.line, target.line, tolerance)
        }
        if index < 0 then GradingLine(movement.line, TurtleLineStatus.EXISTING_BUT_UNEXPECTED, movement.jump)
        else {
          val (_, expectedIndex) = unmatched.remove(index)
          GradingLine(movement.line, TurtleLineStatus.CORRECT, movement.jump, Some(expectedIndex))
        }
      }
    }
    val missing = unmatched.map { (movement, index) =>
      GradingLine(movement.line, TurtleLineStatus.EXPECTED_BUT_MISSING, movement.jump, Some(index))
    }
    (existing ++ missing).toList
  }

  private def sameLine(a: Line[Double], b: Line[Double], tolerance: Double): Boolean =
    (close(a.start, b.start, tolerance) && close(a.end, b.end, tolerance)) ||
      (close(a.start, b.end, tolerance) && close(a.end, b.start, tolerance))

  private def close(a: Point[Double], b: Point[Double], tolerance: Double): Boolean =
    math.abs(a.x - b.x) <= tolerance && math.abs(a.y - b.y) <= tolerance
}
