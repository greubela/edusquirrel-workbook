package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtleDrawingComparison.TurtleComparisonResult
import munit.FunSuite

class TurtleDrawingComparisonSpec extends FunSuite {

  private def build(commands: List[TurtleCommand[Double]]): TurtlePathBuilder[Double] =
    TurtlePathBuilder(Point(0.0, 0.0), commands, TurtlePathBuilder.SnapHeadingDeg)

  private def square(side: Double): List[TurtleCommand[Double]] =
    List.fill(4)(List(TurtleCommand.Forward(side), TurtleCommand.Left(90.0))).flatten

  private def polygon(steps: Int, side: Double, turn: Double): List[TurtleCommand[Double]] =
    List.fill(steps)(List(TurtleCommand.Forward(side), TurtleCommand.Right(turn))).flatten

  private val circle36 = polygon(36, side = 10.0, turn = 10.0)

  /** A pen-up jump to (200, 200) followed by an 80-unit stroke away from every other test drawing. */
  private val strayStroke: List[TurtleCommand[Double]] =
    List(TurtleCommand.PenUp, TurtleCommand.Goto(200.0, 200.0), TurtleCommand.PenDown, TurtleCommand.Forward(80.0))

  private def compareDrawings(target: List[TurtleCommand[Double]], actual: List[TurtleCommand[Double]],
      tolerance: Option[Double]): TurtleComparisonResult =
    TurtleDrawingComparison.compare(build(target), build(actual), tolerance)

  private def assertMatch(target: List[TurtleCommand[Double]], actual: List[TurtleCommand[Double]],
      tolerance: Option[Double] = None): TurtleComparisonResult = {
    val result = compareDrawings(target, actual, tolerance)
    assert(result.matches, clue = result)
    result
  }

  private def assertMismatch(target: List[TurtleCommand[Double]], actual: List[TurtleCommand[Double]],
      tolerance: Option[Double] = None): TurtleComparisonResult = {
    val result = compareDrawings(target, actual, tolerance)
    assert(!result.matches, clue = result)
    result
  }

  test("identical drawings match") {
    val result = assertMatch(square(100), square(100))
    assertEquals(result.missing, Nil)
    assertEquals(result.extra, Nil)
  }

  test("split segments that form the same line match after merge") {
    assertMatch(List(TurtleCommand.Forward(100.0)), List(TurtleCommand.Forward(50.0), TurtleCommand.Forward(50.0)))
  }

  test("reversed drawing direction matches") {
    // Draw the same vertical segment from top to bottom instead of bottom to top.
    assertMatch(List(TurtleCommand.Forward(100.0)),
      List(TurtleCommand.PenUp, TurtleCommand.Forward(100.0), TurtleCommand.PenDown, TurtleCommand.Backward(100.0)))
  }

  test("different segment order matches") {
    // Vertical then horizontal L-shape
    val verticalThenHorizontal = List(TurtleCommand.Forward(40.0), TurtleCommand.Right(90.0), TurtleCommand.Forward(40.0))
    // Same two strokes, but horizontal first: go to corner with pen up, draw horizontal,
    // then return and draw vertical.
    val horizontalThenVertical = List(
      TurtleCommand.PenUp, TurtleCommand.Forward(40.0), TurtleCommand.Right(90.0),
      TurtleCommand.PenDown, TurtleCommand.Forward(40.0),
      TurtleCommand.PenUp, TurtleCommand.Backward(40.0), TurtleCommand.Left(90.0), TurtleCommand.Backward(40.0),
      TurtleCommand.PenDown, TurtleCommand.Forward(40.0))
    assertMatch(verticalThenHorizontal, horizontalThenVertical)
  }

  test("pen-up travel is ignored") {
    val target = List(TurtleCommand.Forward(50.0), TurtleCommand.PenUp, TurtleCommand.Forward(30.0),
      TurtleCommand.PenDown, TurtleCommand.Forward(50.0))
    assertMatch(target, target ++ List(TurtleCommand.PenUp, TurtleCommand.Left(90.0), TurtleCommand.Forward(200.0)))
  }

  test("double-drawn line counts once") {
    assertMatch(List(TurtleCommand.Forward(100.0)),
      List(TurtleCommand.Forward(100.0), TurtleCommand.Backward(100.0), TurtleCommand.Forward(100.0)))
  }

  test("missing and extra segments are reported") {
    // missing fourth side; extra diagonal-ish longer stroke elsewhere
    val threeSides = List(TurtleCommand.Forward(50.0), TurtleCommand.Left(90.0), TurtleCommand.Forward(50.0),
      TurtleCommand.Left(90.0), TurtleCommand.Forward(50.0))
    val result = assertMismatch(square(50), threeSides ++ strayStroke)
    assertEquals(result.missing.size, 1)
    assertEquals(result.extra.size, 1)
  }

  test("deviation within tolerance matches") {
    // Same line but endpoints shifted by 0.3 (< 0.5 default tolerance) via slightly longer stroke from offset start.
    assertMatch(List(TurtleCommand.Forward(100.0)),
      List(TurtleCommand.PenUp, TurtleCommand.Goto(0.0, -0.3), TurtleCommand.PenDown, TurtleCommand.Forward(100.0)),
      tolerance = Some(0.5))
  }

  test("deviation outside tolerance fails") {
    assertMismatch(List(TurtleCommand.Forward(100.0)),
      List(TurtleCommand.PenUp, TurtleCommand.Goto(0.0, -2.0), TurtleCommand.PenDown, TurtleCommand.Forward(100.0)),
      tolerance = Some(0.5))
  }

  test("circle approximations match when identical") {
    val cmds = List(TurtleCommand.fromText("circle", List(40.0)))
    assertMatch(cmds, cmds)
    assert(TurtleDrawingComparison.extractSegments(build(cmds)).nonEmpty)
  }

  test("36-gon and 72-gon of the same circumference match") {
    assertMatch(circle36, polygon(72, side = 5.0, turn = 5.0))
  }

  test("stepped polygon matches a circle command of the same radius") {
    // circle() walks CPython's chords: a half turn, then forward and turn per step, then a half turn back.
    val radius = 10.0 * 36.0 / (2.0 * math.Pi)
    val (steps, chord, turn) = TurtlePathBuilder.arcSteps(radius, 360.0, turnLeft = true)
    val stepped = (TurtleCommand.Left(turn / 2) +: List.fill(steps)(List(
      TurtleCommand.Forward(chord),
      TurtleCommand.Left(turn)
    )).flatten) :+ TurtleCommand.Left(-turn / 2)
    assertMatch(stepped, List(TurtleCommand.fromText("circle", List(radius))))
  }

  test("circle with twice the circumference fails") {
    assertMismatch(circle36, polygon(72, side = 10.0, turn = 5.0))
  }

  test("half circle leaves the target uncovered") {
    val result = assertMismatch(circle36, polygon(18, side = 10.0, turn = 10.0))
    assert(result.missing.nonEmpty, clue = result)
  }

  test("square does not match a circle") {
    assertMismatch(circle36, square(100))
  }

  test("extra stroke beside a matching circle fails") {
    val result = assertMismatch(circle36, circle36 ++ strayStroke)
    assert(result.extra.nonEmpty, clue = result)
  }

}
