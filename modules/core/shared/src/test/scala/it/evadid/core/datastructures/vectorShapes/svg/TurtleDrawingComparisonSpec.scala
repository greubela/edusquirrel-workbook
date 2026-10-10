package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import munit.FunSuite

class TurtleDrawingComparisonSpec extends FunSuite {

  private def build(commands: List[TurtleCommand[Double]]): TurtlePathBuilder[Double] =
    TurtlePathBuilder(Point(0.0, 0.0), commands, BeExpressionToTurtleCommands.SnapHeadingDeg)

  private def square(side: Double): List[TurtleCommand[Double]] =
    List.fill(4)(List(TurtleCommand("forward", List(side)), TurtleCommand("left", List(90.0)))).flatten

  test("identical drawings match") {
    val cmds = square(100)
    val result = TurtleDrawingComparison.compare(build(cmds), build(cmds))
    assert(result.matches, clue = result)
    assertEquals(result.missing, Nil)
    assertEquals(result.extra, Nil)
  }

  test("split segments that form the same line match after merge") {
    val target = build(List(TurtleCommand("forward", List(100.0))))
    val actual = build(List(
      TurtleCommand("forward", List(50.0)),
      TurtleCommand("forward", List(50.0))
    ))
    val result = TurtleDrawingComparison.compare(target, actual)
    assert(result.matches, clue = result)
  }

  test("reversed drawing direction matches") {
    val target = build(List(TurtleCommand("forward", List(100.0))))
    // Draw the same vertical segment from top to bottom instead of bottom to top.
    val actual = build(List(
      TurtleCommand("penup"),
      TurtleCommand("forward", List(100.0)),
      TurtleCommand("pendown"),
      TurtleCommand("backward", List(100.0))
    ))
    val result = TurtleDrawingComparison.compare(target, actual)
    assert(result.matches, clue = result)
  }

  test("different segment order matches") {
    // Vertical then horizontal L-shape
    val verticalThenHorizontal = build(List(
      TurtleCommand("forward", List(40.0)),
      TurtleCommand("right", List(90.0)),
      TurtleCommand("forward", List(40.0))
    ))
    // Same two strokes, but horizontal first: go to corner with pen up, draw horizontal,
    // then return and draw vertical.
    val horizontalThenVertical = build(List(
      TurtleCommand("penup"),
      TurtleCommand("forward", List(40.0)),
      TurtleCommand("right", List(90.0)),
      TurtleCommand("pendown"),
      TurtleCommand("forward", List(40.0)),
      TurtleCommand("penup"),
      TurtleCommand("backward", List(40.0)),
      TurtleCommand("left", List(90.0)),
      TurtleCommand("backward", List(40.0)),
      TurtleCommand("pendown"),
      TurtleCommand("forward", List(40.0))
    ))
    val result = TurtleDrawingComparison.compare(verticalThenHorizontal, horizontalThenVertical)
    assert(result.matches, clue = result)
  }

  test("pen-up travel is ignored") {
    val target = build(List(
      TurtleCommand("forward", List(50.0)),
      TurtleCommand("penup"),
      TurtleCommand("forward", List(30.0)),
      TurtleCommand("pendown"),
      TurtleCommand("forward", List(50.0))
    ))
    val actual = build(List(
      TurtleCommand("forward", List(50.0)),
      TurtleCommand("penup"),
      TurtleCommand("forward", List(30.0)),
      TurtleCommand("pendown"),
      TurtleCommand("forward", List(50.0)),
      TurtleCommand("penup"),
      TurtleCommand("left", List(90.0)),
      TurtleCommand("forward", List(200.0))
    ))
    val result = TurtleDrawingComparison.compare(target, actual)
    assert(result.matches, clue = result)
  }

  test("double-drawn line counts once") {
    val target = build(List(TurtleCommand("forward", List(100.0))))
    val actual = build(List(
      TurtleCommand("forward", List(100.0)),
      TurtleCommand("backward", List(100.0)),
      TurtleCommand("forward", List(100.0))
    ))
    val result = TurtleDrawingComparison.compare(target, actual)
    assert(result.matches, clue = result)
  }

  test("missing and extra segments are reported") {
    val target = build(square(50))
    val actual = build(List(
      TurtleCommand("forward", List(50.0)),
      TurtleCommand("left", List(90.0)),
      TurtleCommand("forward", List(50.0)),
      TurtleCommand("left", List(90.0)),
      TurtleCommand("forward", List(50.0)),
      // missing fourth side; extra diagonal-ish longer stroke elsewhere
      TurtleCommand("penup"),
      TurtleCommand("goto", List(200.0, 200.0)),
      TurtleCommand("pendown"),
      TurtleCommand("forward", List(80.0))
    ))
    val result = TurtleDrawingComparison.compare(target, actual)
    assert(!result.matches)
    assertEquals(result.missing.size, 1)
    assertEquals(result.extra.size, 1)
  }

  test("deviation within tolerance matches") {
    val target = build(List(TurtleCommand("forward", List(100.0))))
    // Same line but endpoints shifted by 0.3 (< 0.5 default tolerance) via slightly longer stroke from offset start.
    val actual = build(List(
      TurtleCommand("penup"),
      TurtleCommand("goto", List(0.0, -0.3)),
      TurtleCommand("pendown"),
      TurtleCommand("forward", List(100.0))
    ))
    val result = TurtleDrawingComparison.compare(target, actual, tolerance = Some(0.5))
    assert(result.matches, clue = result)
  }

  test("deviation outside tolerance fails") {
    val target = build(List(TurtleCommand("forward", List(100.0))))
    val actual = build(List(
      TurtleCommand("penup"),
      TurtleCommand("goto", List(0.0, -2.0)),
      TurtleCommand("pendown"),
      TurtleCommand("forward", List(100.0))
    ))
    val result = TurtleDrawingComparison.compare(target, actual, tolerance = Some(0.5))
    assert(!result.matches, clue = result)
  }

  test("circle approximations match when identical") {
    val cmds = List(TurtleCommand("circle", List(40.0)))
    val result = TurtleDrawingComparison.compare(build(cmds), build(cmds))
    assert(result.matches, clue = result)
    assert(TurtleDrawingComparison.extractSegments(build(cmds)).nonEmpty)
  }

  private def polygon(steps: Int, side: Double, turn: Double): List[TurtleCommand[Double]] =
    List.fill(steps)(List(
      TurtleCommand("forward", List(side)),
      TurtleCommand("turn", List(turn))
    )).flatten

  test("36-gon and 72-gon of the same circumference match") {
    val coarse = build(polygon(36, side = 10.0, turn = 10.0))
    val fine = build(polygon(72, side = 5.0, turn = 5.0))
    val result = TurtleDrawingComparison.compare(coarse, fine)
    assert(result.matches, clue = result)
  }

  test("stepped polygon matches a circle command of the same radius") {
    // circle() turns left, same as repeated left(); chord length equals 2*pi*r/36.
    val radius = 10.0 * 36.0 / (2.0 * math.Pi)
    val stepped = build(List.fill(36)(List(
      TurtleCommand("forward", List(10.0)),
      TurtleCommand("left", List(10.0))
    )).flatten)
    val rounded = build(List(TurtleCommand("circle", List(radius))))
    val result = TurtleDrawingComparison.compare(stepped, rounded)
    assert(result.matches, clue = result)
  }

  test("circle with twice the circumference fails") {
    val target = build(polygon(36, side = 10.0, turn = 10.0))
    val doubled = build(polygon(72, side = 10.0, turn = 5.0))
    val result = TurtleDrawingComparison.compare(target, doubled)
    assert(!result.matches, clue = result)
  }

  test("half circle leaves the target uncovered") {
    val target = build(polygon(36, side = 10.0, turn = 10.0))
    val half = build(polygon(18, side = 10.0, turn = 10.0))
    val result = TurtleDrawingComparison.compare(target, half)
    assert(!result.matches, clue = result)
    assert(result.missing.nonEmpty, clue = result)
  }

  test("square does not match a circle") {
    val circle = build(polygon(36, side = 10.0, turn = 10.0))
    val box = build(square(100))
    val result = TurtleDrawingComparison.compare(circle, box)
    assert(!result.matches, clue = result)
  }

  test("extra stroke beside a matching circle fails") {
    val circle = polygon(36, side = 10.0, turn = 10.0)
    val withStroke = build(circle ++ List(
      TurtleCommand("penup"),
      TurtleCommand("goto", List(200.0, 200.0)),
      TurtleCommand("pendown"),
      TurtleCommand("forward", List(80.0))
    ))
    val result = TurtleDrawingComparison.compare(build(circle), withStroke)
    assert(!result.matches, clue = result)
    assert(result.extra.nonEmpty, clue = result)
  }

}
