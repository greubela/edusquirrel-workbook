package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.core.datastructures.geometry.Point
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.LineResult
import it.evadid.workbook.elements.interactionElements.programming.TurtleGraphic
import munit.FunSuite

class TurtleJsxGraphRendererSpec extends FunSuite {
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
  test("recreate graphics allow different pen-up routes but require real strokes") {
    val expected = TurtleGraphic.TurtleGraphicProgram(List(
      TurtleCommand("penUp"), TurtleCommand("goto", List(10.0, 0.0)),
      TurtleCommand("penDown"), TurtleCommand("forward", List(10.0))))
    val alternative: List[TurtleCommand[Double]] = List(
      TurtleCommand("penUp"), TurtleCommand("goto", List(0.0, 10.0)),
      TurtleCommand("goto", List(10.0, 0.0)), TurtleCommand("penDown"),
      TurtleCommand("forward", List(10.0)))
    val scene = TurtleJsxGraphRenderer.buildScene(alternative, expected, 1e-7, gradeJumps = false)
    assert(scene.lines.forall(_.result == LineResult.Correct))
    assertEquals(scene.lines.count(!_.jump), 1)
    val noStitch = TurtleJsxGraphRenderer.buildScene(
      alternative.filterNot(_.name == "penDown"), expected, 1e-7, gradeJumps = false)
    assertEquals(noStitch.lines.count(_.result == LineResult.Missing), 1)
  }

}
