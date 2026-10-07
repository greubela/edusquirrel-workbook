package it.evadid.core.datastructures.vectorShapes

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath.BuilderBasedSvgPath
import it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines.*
import it.evadid.util.logging.BasicLogger
import munit.FunSuite

class DrawingRoutinesTest extends FunSuite {
  private val logger = BasicLogger()
  private val bounds = Bounds(Point(10.0, 20.0), Dimension(125.0, 50.0))

  private def builderFor(routine: it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine[Double]) =
    routine.renderPath(logger, bounds).asInstanceOf[BuilderBasedSvgPath[Double]].pathBuilder

  private val routines = List(
    BooleanShape[Double](),
    CircleShape[Double](),
    CommandShape[Double](),
    DateShape[Double](),
    DuckShape[Double](),
    LiteralShape[Double](),
    NumericShape[Double](),
    RectangleShape[Double](),
    SnapBooleanShape[Double](),
    SnapCShape[Double](),
    SnapCommandShape[Double](),
    SnapHatShape[Double](),
    SnapReporterShape[Double](),
    StringShape[Double]()
  )

  test("every built-in drawing routine creates a closed path") {
    routines.foreach { routine =>
      val path = routine.renderPath(logger, bounds)
      assert(path.svgPathDString.endsWith(" Z"), clue = s"${routine.getClass.getSimpleName}: ${path.svgPathDString}")
    }
  }

  test("drawing-routine endpoints stay inside their requested bounds") {
    routines.foreach { routine =>
      val builder = builderFor(routine)
      builder.pathPoints.foreach { point =>
        assert(point.x >= bounds.startX && point.x <= bounds.endX, clue = s"${routine.getClass.getSimpleName}: x=${point.x}")
        assert(point.y >= bounds.startY && point.y <= bounds.endY, clue = s"${routine.getClass.getSimpleName}: y=${point.y}")
      }
    }
  }

  test("circle arcs return to their starting point") {
    val builder = builderFor(CircleShape[Double]())

    assertEquals(builder.pathPoints.head, Point(10.0, 20.0))
    assertEquals(builder.pathPoints.drop(1).head, Point(10.0, 45.0))
    assertEquals(builder.pathPoints.init.last, Point(10.0, 45.0))
  }

  test("rectangle uses width for horizontal segments and height for vertical segments") {
    val path = RectangleShape[Double]().renderPath(logger, bounds)

    assertEquals(path.svgPathDString, "M 10 20 l 125 0 l 0 50 l -125 0 l 0 -50 Z")
  }
}
