package interactionPlugins.fileSubmission.turtleLogic

import munit.FunSuite

class TurtleRendererSpec extends FunSuite {

  test("simulateSegments renders a simple forward(100) move") {
    val segments = TurtleRenderer.simulateSegments(List(
      TurtleXmlParser.ReceiveGo,
      TurtleXmlParser.Forward(100)
    ))

    assertEquals(segments.length, 1)
    assertEqualsDouble(segments.head.x1, 0.0, 0.0001)
    assertEqualsDouble(segments.head.y1, 0.0, 0.0001)
    assertEqualsDouble(segments.head.x2, 100.0, 0.0001)
    assertEqualsDouble(segments.head.y2, 0.0, 0.0001)
  }
}
