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

  private def motion(name: String, value: Double): TurtleCommand[Double] =
    TurtleCommand(name, List(value))

  private def traceResult(
      expected: Seq[TurtleCommand[Double]],
      actual: Seq[TurtleCommand[Double]],
      scale: Double = 1.0,
      tolerance: Double = 1e-8
  ): TurtleTraceComparison.Result =
    TurtleTraceComparison.compare(expected, actual, scale, tolerance).fold(
      problem => fail(s"Trace comparison failed: $problem"), identity)

  private def kochCurve(depth: Int, length: Double): List[TurtleCommand[Double]] =
    if depth == 0 then List(motion("forward", length))
    else {
      val part = kochCurve(depth - 1, length / 3.0)
      part ++ List(motion("right", -60.0)) ++ part ++ List(motion("right", 120.0)) ++
        part ++ List(motion("right", -60.0)) ++ part
    }

  test("ordered traces allow subdivision, zero moves and equivalent turns") {
    val expected = List(motion("forward", 1.0), motion("right", 90.0), motion("forward", 1.0))
    val actual = List(
      motion("fd", 0.0), motion("fd", 0.25), motion("fd", 0.75),
      motion("rt", -270.0), motion("fd", -0.0), motion("fd", 0.5), motion("fd", 0.5),
      motion("rt", 17.0)
    )
    val result = traceResult(expected, actual)
    assert(result.matches, clue = result)
    assertEqualsDouble(result.expectedLength, 2.0, 1e-14)
    assertEqualsDouble(result.actualLength, 2.0, 1e-14)
    assertEqualsDouble(result.lengthDeviation, 0.0, 1e-14)
    assertEqualsDouble(result.maxDeviation, 0.0, 1e-14)
  }

  test("ordered Koch traces retain their requested scale without a pixel floor") {
    for {
      depth <- 0 to 4
      length <- List(1e-12, 1.0, 10.0 / 3.0, 100.0, 1e300)
    } {
      val expected = kochCurve(depth, length)
      val subdivided = expected.flatMap { command =>
        if command.name == "forward" then List.fill(2)(motion("forward", command.args.head / 2.0))
        else List(command)
      }
      val matching = traceResult(expected, subdivided, length)
      assert(matching.matches, clue = (depth, length, matching))
      assertEqualsDouble(matching.expectedLength, math.pow(4.0 / 3.0, depth), 1e-12)
      val undersized = traceResult(expected, kochCurve(depth, length * 0.8), length)
      assert(!undersized.matches, clue = (depth, length, undersized))
    }
  }

  test("ordered traces reject reversed, retraced and differently ordered strokes") {
    val line = List(motion("forward", 1.0))
    val reversed = traceResult(line, List(motion("forward", -1.0)))
    assert(!reversed.matches, clue = reversed)
    val retraced = traceResult(line,
      List(motion("forward", 1.0), motion("forward", -1.0), motion("forward", 1.0)))
    assert(!retraced.matches, clue = retraced)
    assertEqualsDouble(retraced.lengthDeviation, 2.0, 1e-14)
    val counterclockwise = List.fill(4)(List(motion("forward", 1.0), motion("right", -90.0))).flatten
    val clockwise = motion("right", -90.0) ::
      List.fill(4)(List(motion("forward", 1.0), motion("right", 90.0))).flatten
    assert(TurtleDrawingComparison.compare(build(counterclockwise), build(clockwise), Some(0.01)).matches)
    val reordered = traceResult(counterclockwise, clockwise)
    assert(!reordered.matches, clue = reordered)
    assertEqualsDouble(reordered.lengthDeviation, 0.0, 1e-14)
  }

  test("ordered traces check interior deviations even when arc lengths are close") {
    val angle = 1.0
    val height = 0.001
    val radians = math.toRadians(angle)
    val leg = height / math.sin(radians)
    val horizontal = height / math.tan(radians)
    val actual = List(
      motion("forward", 0.432), motion("right", -angle), motion("forward", leg),
      motion("right", angle * 2), motion("forward", leg), motion("right", -angle),
      motion("forward", 1.0 - 0.432 - horizontal * 2)
    )
    val result = traceResult(List(motion("forward", 1.0)), actual, tolerance = 1e-4)
    assert(result.lengthDeviation < 1e-4, clue = result)
    assert(result.maxDeviation >= height * 0.999, clue = result)
    assert(!result.matches, clue = result)
  }

  test("ordered trace tolerances and metrics are normalized by the requested scale") {
    val tolerance = 1.0 / 1024.0
    for scale <- List(1.0, 8.0, 1024.0) do {
      val target = List(motion("forward", scale))
      val inside = traceResult(target, List(motion("forward", scale * (1.0 + tolerance))), scale, tolerance)
      assert(inside.matches, clue = inside)
      assertEqualsDouble(inside.expectedLength, 1.0, 1e-14)
      assertEqualsDouble(inside.actualLength, 1.0 + tolerance, 1e-14)
      assertEqualsDouble(inside.lengthDeviation, tolerance, 1e-14)
      assertEqualsDouble(inside.maxDeviation, tolerance, 1e-14)
      val outside = traceResult(target, List(motion("forward", scale * (1.0 + tolerance * 2))), scale, tolerance)
      assert(!outside.matches, clue = outside)
    }
  }

  test("ordered empty traces cannot match a nonzero stroke below tolerance") {
    val zero = List(motion("forward", 0.0), motion("right", -123.0), motion("forward", -0.0))
    val empty = traceResult(Nil, zero)
    assert(empty.matches, clue = empty)
    assertEqualsDouble(empty.expectedLength, 0.0, 0.0)
    assertEqualsDouble(empty.actualLength, 0.0, 0.0)
    assertEqualsDouble(empty.maxDeviation, 0.0, 0.0)
    val small = List(motion("forward", 1e-6))
    assert(!traceResult(Nil, small, tolerance = 1e-3).matches)
    assert(!traceResult(small, Nil, tolerance = 1e-3).matches)
  }

  test("ordered traces require finite positive scale and tolerance") {
    import TurtleTraceComparison.Failure
    val line = List(motion("forward", 1.0))
    for scale <- List(0.0, -1.0, Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity) do
      assertEquals(TurtleTraceComparison.compare(line, line, scale, 1e-8), Left(Failure.InvalidScale))
    for tolerance <- List(0.0, -1.0, Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity) do
      assertEquals(TurtleTraceComparison.compare(line, line, 1.0, tolerance), Left(Failure.InvalidTolerance))
  }

  test("ordered traces reject unsupported commands and malformed numeric arguments") {
    import TurtleTraceComparison.Failure
    val line = List(motion("forward", 1.0))
    for name <- List("left", "lt", "circle", "goto", "penup", "unknown") do
      assertEquals(TurtleTraceComparison.compare(line, List(motion(name, 1.0)), 1.0, 1e-8),
        Left(Failure.UnsupportedCommand))
    val malformed = List(
      TurtleCommand[Double]("forward"),
      TurtleCommand[Double]("forward", List(1.0, 2.0)),
      TurtleCommand[Double]("right", List(90.0), List("extra"))
    )
    malformed.foreach { command =>
      assertEquals(TurtleTraceComparison.compare(List(command), line, 1.0, 1e-8), Left(Failure.InvalidArguments))
      assertEquals(TurtleTraceComparison.compare(line, List(command), 1.0, 1e-8), Left(Failure.InvalidArguments))
    }
    for value <- List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity) do {
      assertEquals(TurtleTraceComparison.compare(List(motion("forward", value)), line, 1.0, 1e-8),
        Left(Failure.NonFiniteValue))
      assertEquals(TurtleTraceComparison.compare(line, List(motion("right", value)), 1.0, 1e-8),
        Left(Failure.NonFiniteValue))
    }
  }

  test("ordered traces bound commands on each side without quadratic sampling") {
    import TurtleTraceComparison.Failure
    val line = List(motion("forward", 1.0))
    val limit = Vector.fill(10000)(motion("forward", 1.0 / 10000.0))
    val result = traceResult(limit, limit, tolerance = 1e-4)
    assert(result.matches, clue = result)
    assertEqualsDouble(result.expectedLength, 1.0, 1e-12)
    val tooMany = limit :+ motion("right", 0.0)
    assertEquals(TurtleTraceComparison.compare(tooMany, line, 1.0, 1e-4), Left(Failure.TooManyCommands))
    assertEquals(TurtleTraceComparison.compare(line, tooMany, 1.0, 1e-4), Left(Failure.TooManyCommands))
  }

  test("ordered traces reject numeric precision loss but retain finite extreme turns") {
    import TurtleTraceComparison.Failure
    val line = List(motion("forward", 1.0))
    val tiny = java.lang.Double.MIN_VALUE
    val lostMovement = List(motion("forward", 1.0), motion("forward", math.ulp(1.0) / 4.0))
    assertEquals(TurtleTraceComparison.compare(line, line, tiny, 1e-8), Left(Failure.NumericRange))
    assertEquals(TurtleTraceComparison.compare(List(motion("forward", tiny)), Nil, Double.MaxValue, 1e-8),
      Left(Failure.NumericRange))
    assertEquals(TurtleTraceComparison.compare(lostMovement, line, 1.0, 1e-8), Left(Failure.NumericRange))
    assertEquals(TurtleTraceComparison.compare(List.fill(2)(motion("forward", Double.MaxValue)), line, 1.0, 1e-8),
      Left(Failure.NumericRange))
    assertEquals(TurtleTraceComparison.compare(line, line, 1.0, 1e-16), Left(Failure.NumericRange))
    val subnormal = List(motion("forward", tiny))
    assertEquals(TurtleTraceComparison.compare(subnormal, subnormal, 1.0, tiny), Left(Failure.NumericRange))
    val small = List(motion("forward", 1e-280))
    assert(traceResult(small, small, tolerance = 1e-281).matches)
    val balanced = List(motion("right", Double.MaxValue), motion("right", -Double.MaxValue), motion("forward", 1.0))
    assert(traceResult(line, balanced).matches)
    val reduced = Double.MaxValue % 360.0
    val afterHeading = List(motion("right", 17.0), motion("right", Double.MaxValue), motion("forward", 1.0))
    assert(traceResult(List(motion("right", 17.0 + reduced), motion("forward", 1.0)), afterHeading).matches)
    val repeated = List.fill(4)(motion("right", Double.MaxValue)) :+ motion("forward", 1.0)
    assert(traceResult(List(motion("right", reduced * 4), motion("forward", 1.0)), repeated).matches)
  }
}
