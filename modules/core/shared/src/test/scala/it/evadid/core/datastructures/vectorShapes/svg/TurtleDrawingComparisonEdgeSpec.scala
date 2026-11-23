package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtleDrawingComparison.{Segment, compareSegments}
import munit.FunSuite

class TurtleDrawingComparisonEdgeSpec extends FunSuite {
  private def p(x: Double, y: Double) = Point(x, y)

  test("comparing against nothing reports every segment with the finite deviation of the other side") {
    val drawn = Seq(Segment(p(0, 0), p(10, 0)))
    val onlyTarget = compareSegments(drawn, Nil, 2.0)
    assert(!onlyTarget.matches)
    assertEquals((onlyTarget.missing, onlyTarget.extra, onlyTarget.maxDeviation), (drawn, Nil, 0.0))
    val onlyActual = compareSegments(Nil, drawn, 2.0)
    assertEquals((onlyActual.missing, onlyActual.extra), (Nil, drawn))
    val empty = compareSegments(Nil, Nil, 3.0)
    assert(empty.matches)
    assertEquals(empty.tolerance, 3.0)
  }

  test("zero-length segments are compared by their single point") {
    val dot = Seq(Segment(p(5, 5), p(5, 5)))
    assert(compareSegments(Seq(Segment(p(0, 5), p(10, 5))), dot, 1.0).extra.isEmpty)
    val away = compareSegments(dot, Seq(Segment(p(5, 9), p(5, 9))), 1.0)
    assertEqualsDouble(away.maxDeviation, 4.0, 1e-9)
  }

  test("relative turtle moves and pen-up travel are converted to absolute drawn segments") {
    val square = TurtlePathBuilder(Point(0.0, 0.0), List(TurtleCommand.PenUp[Double](), TurtleCommand.Forward(5.0), TurtleCommand.PenDown[Double](),
      TurtleCommand.Forward(10.0), TurtleCommand.Right(90.0), TurtleCommand.Forward(10.0)))
    val segments = TurtleDrawingComparison.extractSegments(square)
    assertEquals(segments.size, 2)
    assertEqualsDouble(segments.map(_.length).sum, 20.0, 1e-9)
    assertEquals(TurtleDrawingComparison.defaultTolerance(Nil), TurtleDrawingComparison.MinTolerance)
    assert(TurtleDrawingComparison.compare(square, square).matches)
  }
}
