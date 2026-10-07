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

  test("missing expected moves retain their dashed style") {
    val expected = List(
      TurtleJsxGraphRenderer.LineToRender(Point(0.0, 0.0), Point(10.0, 0.0), jump = true)
    )

    val scene = TurtleJsxGraphRenderer.buildScene(List.empty, expected)

    assertEquals(scene.lines.map(_.result), List(LineResult.Missing))
    assertEquals(scene.lines.map(_.jump), List(true))
  }
}
