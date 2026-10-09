package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.core.datastructures.geometry.Point
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.LineResult
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingState, ProgrammingStateJavaString, ProgrammingStatePythonString, TurtleGraphic}
import munit.FunSuite

class TurtleJsxGraphRendererSpec extends FunSuite {
  test("preview conversion retains Java drafts it cannot display") {
    List("class Drawing {", "class Drawing { static void main(String[] args) {} }").foreach { code =>
      val state = ProgrammingStateJavaString(code)
      val fingerprint = ProgrammingState.fingerprint(state)
      assert(HtmlTurtleRecreateShapeRenderer.commandsForPreview(state).isFailure)
      assertEquals(ProgrammingState.fingerprint(state), fingerprint)
      assertEquals(state.code, code)
    }
  }

  test("preview conversion still derives supported turtle commands") {
    assertEquals(
      HtmlTurtleRecreateShapeRenderer.commandsForPreview(ProgrammingStatePythonString("forward(12)")).get,
      List(TurtleCommand[Double]("forward", List(12.0)))
    )
  }

  private def assertPoint(actual: Point[Double], expected: Point[Double]): Unit = {
    assertEqualsDouble(actual.x, expected.x, 1e-7)
    assertEqualsDouble(actual.y, expected.y, 1e-7)
  }

  private def assertEqualsDouble(actual: Double, expected: Double, tolerance: Double): Unit =
    assert(Math.abs(actual - expected) <= tolerance, s"Expected $expected but got $actual")

  test("graphic rendering uses expected geometry and expected angles") {
    val actual = List(
      TurtleCommand("forward", List(10.0)),
      TurtleCommand("turnRight", List(30.0)),
      TurtleCommand("forward", List(10.0))
    )
    val expected = TurtleGraphic.TurtleGraphicProgram(List(
      TurtleCommand("forward", List(10.0)),
      TurtleCommand("turnLeft", List(90.0)),
      TurtleCommand("forward", List(10.0))
    ))

    val scene = TurtleJsxGraphRenderer.buildScene(actual, expected)

    assertEquals(scene.lines.map(_.result), List(LineResult.Correct, LineResult.Unexpected, LineResult.Missing))
    assertEquals(scene.angles.map(_.degrees), List(90.0))
    assertEquals(scene.angles.map(_.lineBefore), List(0))
    assertEquals(scene.angles.map(_.lineAfter), List(2))
  }

  test("turns preserve the expected positions of consecutive lines") {
    val expected = TurtleGraphic.TurtleLineBasedProgram(List(
      TurtleGraphic.Line(Point(0.0, 0.0), Point(100.0, 0.0)),
      TurtleGraphic.Line(Point(100.0, 0.0), Point(100.0, 100.0)),
      TurtleGraphic.Line(Point(100.0, 100.0), Point(200.0, 100.0))
    ))

    val scene = TurtleJsxGraphRenderer.buildScene(expected.toTurtleProgram.toList, expected)
    val expectedLines = List(
      (Point(0.0, 0.0), Point(100.0, 0.0)),
      (Point(100.0, 0.0), Point(100.0, 100.0)),
      (Point(100.0, 100.0), Point(200.0, 100.0))
    )

    assertEquals(scene.lines.map(_.result), List.fill(3)(LineResult.Correct))
    assertEquals(scene.lines.size, expectedLines.size)
    scene.lines.zip(expectedLines).foreach { case (actual, (start, end)) =>
      assertPoint(actual.start, start)
      assertPoint(actual.end, end)
    }
  }

  test("pen-up movements are dashed and pen-down goto movements are solid") {
    val program: List[TurtleCommand[Double]] = List(
      TurtleCommand("penUp"),
      TurtleCommand("forward", List(10.0)),
      TurtleCommand("penDown"),
      TurtleCommand("goto", List(20.0, 0.0))
    )

    val scene = TurtleJsxGraphRenderer.buildScene(program, List.empty[TurtleJsxGraphRenderer.LineToRender[Double]])

    assertEquals(scene.lines.map(_.jump), List(true, false))
  }

  test("angle sectors lie between adjacent segments at both corners of a stepped path") {
    val expected = TurtleGraphic.TurtleLineBasedProgram(List(
      TurtleGraphic.Line(Point(0.0, 0.0), Point(100.0, 0.0)),
      TurtleGraphic.Line(Point(100.0, 0.0), Point(100.0, 100.0)),
      TurtleGraphic.Line(Point(100.0, 100.0), Point(200.0, 100.0))
    ))
    // The actual program misses both turns. Markers must still
    // follow the expected segments, including the two missing ones.
    val actual = List(TurtleCommand("forward", List(100.0)), TurtleCommand("forward", List(100.0)))
    val scene = TurtleJsxGraphRenderer.buildScene(actual, expected)
    val sectors = scene.angles.map(TurtleJsxGraphRenderer.angleSector(scene, _, 10.0))

    assertPoint(sectors(0).first, Point(90.0, 0.0))
    assertPoint(sectors(0).last, Point(100.0, -10.0))
    assertPoint(sectors(1).first, Point(110.0, -100.0))
    assertPoint(sectors(1).last, Point(100.0, -90.0))
    sectors.foreach(sector => assertEqualsDouble(sector.degrees, 90.0, 1e-7))
  }

  test("non-right-angle sectors use the angle between the lines and retain SVG y conversion") {
    val program = List(
      TurtleCommand("forward", List(10.0)),
      TurtleCommand("turnLeft", List(60.0)),
      TurtleCommand("forward", List(10.0))
    )
    val scene = TurtleJsxGraphRenderer.buildScene(program, List.empty[TurtleJsxGraphRenderer.LineToRender[Double]])
    val sector = TurtleJsxGraphRenderer.angleSector(scene, scene.angles.head, 2.0)

    assertPoint(sector.first, Point(11.0, math.sqrt(3.0)))
    assertPoint(sector.last, Point(8.0, 0.0))
    assertEqualsDouble(sector.degrees, 120.0, 1e-7)
  }

  test("angle rays also follow direction-independent matched segments") {
    import TurtleJsxGraphRenderer.{RenderedAngle, RenderedLine, Scene}
    val vertex = Point(100.0, 100.0)
    val angle = RenderedAngle(vertex, -90.0, 90.0, 0, 1)
    val scene = Scene(List(
      RenderedLine(vertex, Point(100.0, 0.0), LineResult.Correct, false),
      RenderedLine(Point(200.0, 100.0), vertex, LineResult.Correct, false)
    ), List(angle))
    val sector = TurtleJsxGraphRenderer.angleSector(scene, angle, 10.0)

    assertPoint(sector.first, Point(110.0, -100.0))
    assertPoint(sector.last, Point(100.0, -90.0))
    assertEqualsDouble(sector.degrees, 90.0, 1e-7)
  }

  test("missing expected moves retain their dashed style") {
    val expected = List(
      TurtleJsxGraphRenderer.LineToRender(Point(0.0, 0.0), Point(10.0, 0.0), jump = true)
    )

    val scene = TurtleJsxGraphRenderer.buildScene(List.empty, expected)

    assertEquals(scene.lines.map(_.result), List(LineResult.Missing))
    assertEquals(scene.lines.map(_.jump), List(true))
  }

  private def command(name: String, args: Double*): TurtleCommand[Double] = TurtleCommand(name, args.toList)

  private def graphic(commands: List[TurtleCommand[Double]]): TurtleGraphic =
    TurtleGraphic.TurtleGraphicProgram(commands)

  test("coverage grading accepts subdivision and repeated drawing without changing legacy matching") {
    val expected = graphic(List(command("forward", 10)))
    val split = List(command("forward", 5), command("forward", 5))
    val repeated = List(command("forward", 10), command("backward", 10), command("forward", 10))
    List(split, repeated).foreach { commands =>
      val result = TurtleDrawingGrading.compare(commands, expected)
      assert(result.matches, clue = result)
      val scene = TurtleDrawingGrading.assessedScene(commands, expected, result)
      assertEquals(scene.lines.map(_.result), List(LineResult.Correct))
      assertEquals(scene.lines.map(_.jump), List(false))
    }
    assertEquals(TurtleJsxGraphRenderer.buildScene(split, expected).lines.map(_.result),
      List(LineResult.Unexpected, LineResult.Unexpected, LineResult.Missing))
    assertEquals(TurtleJsxGraphRenderer.buildScene(repeated, expected).lines.map(_.result),
      List(LineResult.Correct, LineResult.Unexpected, LineResult.Unexpected))
  }

  test("coverage grading ignores pen-up movement and accepts reversed strokes") {
    val expected = graphic(List(command("forward", 10)))
    val reversed = List(command("penup"), command("forward", 10), command("pendown"),
      command("backward", 10), command("penup"), command("goto", 50, 50))
    assert(TurtleDrawingGrading.compare(reversed, expected).matches)
    val scene = TurtleDrawingGrading.assessedScene(reversed, expected)
    assertEquals(scene.lines.map(_.result), List(LineResult.Correct))
    assertEquals(scene.lines.map(_.jump), List(false))
    assertPoint(scene.lines.head.start, Point(0.0, 0.0))
    assertPoint(scene.lines.head.end, Point(10.0, 0.0))
  }

  test("coverage colors agree with missing and extra geometry") {
    val expected = graphic(List(command("forward", 10), command("right", 90), command("forward", 10)))
    val actual = List(command("forward", 10), command("penup"), command("goto", 30, 30),
      command("pendown"), command("forward", 5))
    val result = TurtleDrawingGrading.compare(actual, expected)
    assert(!result.matches)
    val scene = TurtleDrawingGrading.assessedScene(actual, expected, result)
    assertEquals(scene.lines.map(_.result), List(LineResult.Correct, LineResult.Missing, LineResult.Unexpected))
    assertPoint(scene.lines.last.start, Point(30.0, 30.0))
    assertPoint(scene.lines.last.end, Point(35.0, 30.0))
    assertEquals(scene.angles.map(angle => (angle.lineBefore, angle.lineAfter)), List((0, 1)))
    assertEqualsDouble(TurtleJsxGraphRenderer.angleSector(scene, scene.angles.head, 2).degrees, 90.0, 1e-7)
  }

  test("coverage retains target angles across subdivisions, reversed strokes and nearby lines") {
    val expected = graphic(List(command("penup"), command("forward", 2), command("pendown"),
      command("forward", 10), command("right", 90), command("forward", 10)))
    val split = List(command("penup"), command("forward", 2), command("pendown"),
      command("forward", 5), command("forward", 5), command("right", 90), command("forward", 5), command("forward", 5))
    val reversed = List(command("penup"), command("goto", 12, 10), command("pendown"),
      command("goto", 12, 0), command("goto", 2, 0))
    val nearby = List(command("penup"), command("forward", 2), command("pendown"),
      command("forward", 9.9), command("right", 90), command("forward", 10))
    List(split, reversed, nearby).foreach { commands =>
      assert(TurtleDrawingGrading.compare(commands, expected).matches)
      val scene = TurtleDrawingGrading.assessedScene(commands, expected)
      assertEquals(scene.lines.map(_.result), List(LineResult.Correct, LineResult.Correct))
      assertEquals(scene.angles.map(angle => (angle.lineBefore, angle.lineAfter)), List((0, 1)))
      assertPoint(scene.angles.head.vertex, Point(12.0, 0.0))
      assertEqualsDouble(TurtleJsxGraphRenderer.angleSector(scene, scene.angles.head, 2).degrees, 90.0, 1e-7)
    }
  }

  test("idle and ungraded drawings remain neutral and omit jump geometry") {
    val commands = List(command("penup"), command("forward", 2), command("right", 45),
      command("pendown"), command("forward", 10), command("left", 90), command("forward", 10))
    val scene = TurtleDrawingGrading.targetScene(graphic(commands))
    assertEquals(scene, TurtleDrawingGrading.drawingScene(commands))
    assertEquals(scene.lines.map(_.result), List(LineResult.Neutral, LineResult.Neutral))
    assertEquals(scene.lines.map(_.jump), List(false, false))
    assertEquals(scene.angles.map(angle => (angle.lineBefore, angle.lineAfter)), List((0, 1)))
    scene.angles.foreach(angle => assert(TurtleJsxGraphRenderer.angleSector(scene, angle, 2).degrees.isFinite))
  }

  test("coverage scenes preserve clear, reset and home behavior") {
    val clear = List(command("forward", 10), command("right", 90), command("clear"), command("forward", 5))
    val reset = List(command("forward", 10), command("right", 90), command("reset"), command("forward", 5))
    assertPoint(TurtleDrawingGrading.drawingScene(clear).lines.head.start, Point(10.0, 0.0))
    assertPoint(TurtleDrawingGrading.drawingScene(clear).lines.head.end, Point(10.0, 5.0))
    assertPoint(TurtleDrawingGrading.drawingScene(reset).lines.head.start, Point(0.0, 0.0))
    assertPoint(TurtleDrawingGrading.drawingScene(reset).lines.head.end, Point(5.0, 0.0))
    val home = List(command("forward", 10), command("right", 45), command("home"), command("forward", 5))
    assert(TurtleDrawingGrading.compare(home, graphic(List(command("forward", 10)))).matches)
    assertEquals(TurtleDrawingGrading.drawingScene(home).angles, Nil)
    List(clear, reset).foreach { commands =>
      assert(TurtleDrawingGrading.compare(commands, graphic(commands)).matches)
    }
  }

  test("coverage comparison retains its absolute tolerance") {
    val expected = graphic(List(command("forward", 10)))
    val inside = List(command("penup"), command("goto", 0, 0.25), command("pendown"), command("forward", 10))
    val outside = List(command("penup"), command("goto", 0, 0.251), command("pendown"), command("forward", 10))
    assert(TurtleDrawingGrading.compare(inside, expected).matches)
    assert(!TurtleDrawingGrading.compare(outside, expected).matches)
  }

  test("coverage rejects invalid commands and nonfinite arguments before interpretation") {
    val invalid = List(command("forward"), command("forward", 1, 2), command("penup", 1),
      command("goto", 1), command("circle", 10), command("unknown"),
      TurtleCommand("forward", List(1.0), List("extra"))) ++
      List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity).map(value => command("forward", value))
    invalid.foreach { cmd =>
      intercept[IllegalArgumentException](TurtleDrawingGrading.validateDrawing(List(cmd)))
      intercept[IllegalArgumentException](TurtleDrawingGrading.targetScene(graphic(List(cmd))))
    }
  }

  test("coverage rejects intermediate overflow and nonfinite display bounds") {
    val overflow = List(command("forward", Double.MaxValue), command("forward", Double.MaxValue), command("reset"))
    intercept[IllegalArgumentException](TurtleDrawingGrading.validateDrawing(overflow))
    intercept[IllegalArgumentException](TurtleDrawingGrading.validateDrawing(List(command("goto", Double.MaxValue, 0))))
    intercept[IllegalArgumentException](TurtleDrawingGrading.compare(Nil, graphic(overflow)))
  }

  test("coverage bounds sample, comparison and command workloads") {
    val expected = graphic(List(command("forward", 1250)))
    intercept[IllegalArgumentException](TurtleDrawingGrading.compare(List(command("forward", 1250)), expected))
    val manySegments = List.fill(1001)(command("forward", 0.001))
    intercept[IllegalArgumentException](TurtleDrawingGrading.compare(manySegments, graphic(manySegments)))
    intercept[IllegalArgumentException](TurtleDrawingGrading.validateDrawing(List.fill(10001)(command("forward", 0))))
  }

  test("coverage handles empty and zero-only drawings without assigning a success color at rest") {
    val zero = List(command("forward", 0), command("right", 90), command("forward", -0.0))
    val empty = graphic(Nil)
    assert(TurtleDrawingGrading.compare(zero, empty).matches)
    assertEquals(TurtleDrawingGrading.targetScene(empty).lines, Nil)
    assertEquals(TurtleDrawingGrading.assessedScene(zero, empty).lines, Nil)
    assert(!TurtleDrawingGrading.compare(List(command("forward", 1)), empty).matches)
    assertEquals(TurtleDrawingGrading.assessedScene(List(command("forward", 1)), empty).lines.map(_.result),
      List(LineResult.Unexpected))
  }
}
