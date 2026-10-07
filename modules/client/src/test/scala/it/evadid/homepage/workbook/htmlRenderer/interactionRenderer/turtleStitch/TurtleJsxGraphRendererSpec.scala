package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.LineResult
import it.evadid.workbook.elements.interactionElements.programming.TurtleGraphic
import munit.FunSuite

class TurtleJsxGraphRendererSpec extends FunSuite {
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
}
