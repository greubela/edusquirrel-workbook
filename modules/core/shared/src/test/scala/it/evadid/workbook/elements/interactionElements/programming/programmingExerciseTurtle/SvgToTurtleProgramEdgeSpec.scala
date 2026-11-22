package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder
import munit.FunSuite

class SvgToTurtleProgramEdgeSpec extends FunSuite {
  private def end(path: String): (Double, Double) = {
    val state = TurtlePathBuilder(Point(0.0, 0.0), SvgToTurtleProgram(path).toList).turtleState
    (math.rint(state.x * 1e6) / 1e6, math.rint(state.y * 1e6) / 1e6)
  }

  test("relative and absolute curve commands end at their SVG end points") {
    assertEquals(end("M0 0 H10 V5"), (10.0, 5.0))
    assertEquals(end("M10 10 h-4 v-6"), (6.0, 4.0))
    assertEquals(end("M0 0 c5 0 5 5 10 5 s5 5 10 5"), (20.0, 10.0))
    assertEquals(end("M0 0 C5 0 5 5 10 5 S15 10 20 10"), (20.0, 10.0))
    assertEquals(end("M0 0 S5 5 10 5"), (10.0, 5.0))
    assertEquals(end("M0 0 q5 0 5 5 t5 5"), (10.0, 10.0))
    assertEquals(end("M0 0 T5 5"), (5.0, 5.0))
    assertEquals(end("M0 0 a5 5 0 1 0 10 0"), (10.0, 0.0))
  }

  test("degenerate and undersized arcs still reach their end point") {
    assertEquals(end("M0 0 A0 5 0 0 1 10 0"), (10.0, 0.0))
    assertEquals(end("M0 0 A5 5 0 0 1 0 0"), (0.0, 0.0))
    assertEquals(end("M0 0 A1 1 30 0 0 20 0"), (20.0, 0.0))
    assertEquals(end("M0 0 A10 5 0 1 1 20 0"), (20.0, 0.0))
  }

  test("malformed path data is rejected with the missing parameters named") {
    val messages = Seq("10 10", "M0", "M0 0 L1", "M0 0 H", "M0 0 V", "M0 0 C1 2 3", "M0 0 S1 2", "M0 0 Q1 2", "M0 0 T1",
      "M0 0 A1 1 0 0", "M0 0 A1 1 0 2 0 5 5", "M0 0 X5", "M0 0 L1 1 #").map { path =>
      intercept[IllegalArgumentException](SvgToTurtleProgram(path)).getMessage
    }
    assert(messages.head.contains("must begin with a command"), messages.head)
    assert(messages(1).contains("Move command"), messages(1))
    assert(messages(2).contains("Line command"), messages(2))
    assert(messages(3).contains("Horizontal line"), messages(3))
    assert(messages(4).contains("Vertical line"), messages(4))
    assert(messages(5).contains("Cubic curve"), messages(5))
    assert(messages(6).contains("Smooth cubic"), messages(6))
    assert(messages(7).contains("Quadratic curve"), messages(7))
    assert(messages(8).contains("Smooth quadratic"), messages(8))
    assert(messages(9).contains("Arc requires"), messages(9))
    assert(messages(10).contains("arc flag"), messages(10))
    assert(messages(11).contains("Invalid SVG path data") || messages(11).contains("Unsupported"), messages(11))
    assert(messages(12).contains("Invalid SVG path data near '#'"), messages(12))
  }
}
