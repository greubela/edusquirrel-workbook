package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import munit.FunSuite

class TurtlePathBuilderSpec extends FunSuite {

  test("consecutive geometry with the same pen style stays one segment") {
    val builder = TurtlePathBuilder[Double](
      Point(0.0, 0.0),
      List(
        TurtleCommand("color", stringArgs = List("red")),
        TurtleCommand("forward", List(10.0)),
        TurtleCommand("right", List(90.0)),
        TurtleCommand("forward", List(10.0))
      ),
      90
    )
    val segments = builder.completedStyledSegments
    assertEquals(segments.size, 1)
    assertEquals(segments.head.style.color, "red")
    assert(segments.head.pathBuilder.furtherCommands.size >= 2)
  }

  test("pen color and size changes split segments") {
    val builder = TurtlePathBuilder[Double](
      Point(0.0, 0.0),
      List(
        TurtleCommand("color", stringArgs = List("red")),
        TurtleCommand("pensize", List(2.0)),
        TurtleCommand("forward", List(10.0)),
        TurtleCommand("color", stringArgs = List("blue")),
        TurtleCommand("pensize", List(4.0)),
        TurtleCommand("forward", List(10.0))
      ),
      90
    )
    val segments = builder.completedStyledSegments
    assertEquals(segments.map(_.style.color), List("red", "blue"))
    assertEquals(segments.map(_.style.size), List(2.0, 4.0))
  }

  test("pen-up gaps stay in the current style instead of flushing") {
    val builder = TurtlePathBuilder[Double](
      Point(0.0, 0.0),
      List(
        TurtleCommand("color", stringArgs = List("red")),
        TurtleCommand("forward", List(10.0)),
        TurtleCommand("penup"),
        TurtleCommand("forward", List(10.0)),
        TurtleCommand("pendown"),
        TurtleCommand("forward", List(10.0))
      ),
      90
    )
    val segments = builder.completedStyledSegments
    assertEquals(segments.size, 1)
    assert(builder.svgPathBuilder.toSvgPathD.contains("M") || builder.svgPathBuilder.furtherCommands.nonEmpty)
  }

  test("clear drops previous styled segments") {
    val builder = TurtlePathBuilder[Double](
      Point(0.0, 0.0),
      List(
        TurtleCommand("forward", List(20.0)),
        TurtleCommand("clear"),
        TurtleCommand("forward", List(10.0))
      ),
      90
    )
    val segments = builder.completedStyledSegments
    assertEquals(segments.size, 1)
    assertEquals(builder.turtleState.headingDeg, 90.0)
  }

  test("Snap heading starts at 90 degrees and updates bounds") {
    val builder = TurtlePathBuilder[Double](Point(0.0, 0.0), List(TurtleCommand("forward", List(50.0))), 90)
    assertEquals(builder.turtleState.x, 0.0)
    assertEquals(builder.turtleState.y, -50.0)
    assert(builder.svgPathBuilder.pathPoints.exists(_.y == -50.0))
  }

  test("default pen color is black and width is 1") {
    val builder = TurtlePathBuilder[Double](Point(0.0, 0.0), List(TurtleCommand("forward", List(5.0))), 90)
    val segments = builder.completedStyledSegments
    assertEquals(segments.head.style.color, "black")
    assertEquals(segments.head.style.size, 1.0)
  }
}
