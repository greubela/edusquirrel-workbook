package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleGraphic, TurtleGradingLogic}
import TurtleGraphic.Movement
import TurtleGradingLogic.{TurtleGraphicComparison, TurtleLineStatus}
import TurtleLineStatus.*
import munit.FunSuite

class TurtleGradingLogicSpec extends FunSuite {
  private def cmd(name: String, args: Double*): TurtleCommand[Double] = TurtleCommand(name, args.toList)
  private def graphic(commands: TurtleCommand[Double]*): TurtleGraphic = TurtleGraphic.TurtleGraphicProgram(commands.toList)
  private val stroke = Movement(Point(0.0, 0.0), Point(10.0, 0.0))

  test("comparison reads the expected graphic and retains missing geometry and angle references") {
    val comparison = TurtleGraphicComparison(graphic(cmd("forward", 10)), graphic(cmd("forward", 10), cmd("right", 90), cmd("forward", 5)))
    assertEquals(comparison.linesOfActual.size, 1)
    assertEquals(comparison.linesOfExpected.size, 2)
    assertEquals(comparison.difference.map(_.status), List(CORRECT, EXPECTED_BUT_MISSING))
    assertEquals(comparison.angles.map(a => (a.lineBefore, a.lineAfter)), List((0, 1)))
  }

  test("direction and traversal order do not affect one-to-one matching") {
    val other = Movement(Point(20.0, 0.0), Point(30.0, 0.0))
    val actual = List(other.copy(start = other.end, end = other.start), stroke.copy(start = stroke.end, end = stroke.start))
    val difference = TurtleGradingLogic.compareLines(actual, List(stroke, other))
    assertEquals(difference.map(_.status), List(CORRECT, CORRECT))
    assertEquals(difference.map(_.expectedIndex), List(Some(1), Some(0)))
  }

  test("each expected occurrence is consumed once and extra strokes remain unexpected") {
    val difference = TurtleGradingLogic.compareLines(List(stroke, stroke, stroke), List(stroke, stroke))
    assertEquals(difference.map(_.status), List(CORRECT, CORRECT, EXISTING_BUT_UNEXPECTED))
    assertEquals(difference.map(_.expectedIndex), List(Some(0), Some(1), None))
  }

  test("strict comparison distinguishes pen-up travel from a drawn stroke in both directions") {
    for (actual, expected) <- List((stroke.copy(jump = true), stroke), (stroke, stroke.copy(jump = true))) do {
      val difference = TurtleGradingLogic.compareLines(List(actual), List(expected))
      assertEquals(difference.map(_.status), List(EXISTING_BUT_UNEXPECTED, EXPECTED_BUT_MISSING))
      assertEquals(difference.map(_.jump), List(actual.jump, expected.jump))
    }
  }

  test("stitch comparison accepts alternate travel but travel cannot satisfy a missing stroke") {
    val expected = graphic(cmd("penup"), cmd("goto", 10, 0), cmd("pendown"), cmd("forward", 10))
    val actual = graphic(cmd("penup"), cmd("goto", 0, 10), cmd("goto", 10, 0), cmd("pendown"), cmd("forward", 10))
    val result = TurtleGraphicComparison(actual, expected, gradeJumps = false)
    assert(result.difference.forall(_.status == CORRECT))
    assertEquals(result.angles, Nil)
    val penUpOnly = graphic(cmd("penup"), cmd("goto", 10, 0), cmd("forward", 10))
    assertEquals(TurtleGraphicComparison(penUpOnly, expected, gradeJumps = false).difference.count(_.status == EXPECTED_BUT_MISSING), 1)
  }

  test("tolerance is inclusive and applies to both endpoints; invalid tolerances are rejected") {
    val nearby = stroke.copy(start = Point(0.25, -0.25), end = Point(10.25, 0.25))
    assertEquals(TurtleGradingLogic.compareLines(List(nearby), List(stroke), tolerance = 0.25).map(_.status), List(CORRECT))
    assertEquals(TurtleGradingLogic.compareLines(List(nearby), List(stroke), tolerance = 0.2).map(_.status), List(EXISTING_BUT_UNEXPECTED, EXPECTED_BUT_MISSING))
    assertEquals(TurtleGradingLogic.compareLines(List(stroke), List(stroke), tolerance = 0).map(_.status), List(CORRECT))
    for invalid <- List(-1.0, Double.NaN, Double.PositiveInfinity) do {
      intercept[IllegalArgumentException](TurtleGraphicComparison(graphic(), graphic(), invalid))
      intercept[IllegalArgumentException](TurtleGradingLogic.compareLines(Nil, Nil, invalid))
    }
  }

  test("duplicate expected strokes keep distinct angle references") {
    val expected = graphic(cmd("forward", 10), cmd("backward", 10), cmd("left", 90), cmd("forward", 10))
    val comparison = TurtleGraphicComparison(graphic(cmd("forward", 10)), expected)
    assertEquals(comparison.difference.map(_.expectedIndex), List(Some(0), Some(1), Some(2)))
    assertEquals(comparison.angles.map(a => (a.lineBefore, a.lineAfter)), List((1, 2)))
  }

  test("empty, unexpected-only and missing-only comparisons retain deterministic order") {
    assertEquals(TurtleGradingLogic.compareLines(Nil, Nil), Nil)
    assertEquals(TurtleGradingLogic.compareLines(List(stroke), Nil).map(_.status), List(EXISTING_BUT_UNEXPECTED))
    assertEquals(TurtleGradingLogic.compareLines(Nil, List(stroke)).map(_.status), List(EXPECTED_BUT_MISSING))
  }

  test("nonfinite explicit target geometry is rejected before it reaches a renderer") {
    intercept[IllegalArgumentException](TurtleGradingLogic.compareLines(List(stroke), List(stroke.copy(end = Point(Double.NaN, 0.0)))))
  }

  test("subdividing a stroke does not silently satisfy a different target segment") {
    val halves = List(Movement(Point(0.0, 0.0), Point(5.0, 0.0)), Movement(Point(5.0, 0.0), Point(10.0, 0.0)))
    assertEquals(TurtleGradingLogic.compareLines(halves, List(stroke)).map(_.status),
      List(EXISTING_BUT_UNEXPECTED, EXISTING_BUT_UNEXPECTED, EXPECTED_BUT_MISSING))
  }
}
