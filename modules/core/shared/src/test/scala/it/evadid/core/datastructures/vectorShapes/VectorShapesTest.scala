package it.evadid.core.datastructures.vectorShapes

import munit.FunSuite
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppShapeComposition
import it.evadid.core.datastructures.vectorShapes.atomar.AppShapeDrawingRoutineElement
import it.evadid.core.datastructures.vectorShapes.compositions.{CompositionGrid, CompositionHBox, CompositionVBox}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent
import it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines.{CircleShape, RectangleShape}
import it.evadid.core.datastructures.matrix.Matrix

class VectorShapesTest extends FunSuite {
  private val renderingConfig = AppShapeRenderingConfig.defaultDouble.copy(
    defaultPadding = Dimension(2.0, 3.0),
    gapBetweenConsecutiveShapes = Dimension(5.0, 7.0)
  )
  private val shapeConfig = AppShapeElementConfig.EvaShapeConfigDefault[Double]

  private def rectangle(width: Double, height: Double) =
    AppShapeDrawingRoutineElement(RectangleShape[Double](), shapeConfig, Some(Dimension(width, height)))

  test("an atomic shape renders inside rather than beyond its padding") {
    val rendered = rectangle(10, 20).renderWithMinimumDimension(renderingConfig)

    assertEquals(rendered.myBounds, Bounds(Point(2.0, 3.0), Dimension(10.0, 20.0)))
  }

  test("explicit bounds preserve their origin and reserve padding for shape content") {
    val rendered = rectangle(1, 1).renderComposition(
      renderingConfig,
      Bounds(Point(100.0, 200.0), Dimension(50.0, 40.0))
    )

    assertEquals(rendered.myBounds, Bounds(Point(102.0, 203.0), Dimension(48.0, 37.0)))
  }

  test("bounds smaller than padding are rejected instead of producing negative dimensions") {
    intercept[IllegalArgumentException] {
      rectangle(1, 1).renderComposition(
        renderingConfig,
        Bounds(Point(10.0, 20.0), Dimension(1.0, 2.0))
      )
    }
  }

  test("negative atomic minimum dimensions are rejected") {
    intercept[IllegalArgumentException] {
      rectangle(-1, 10).renderWithMinimumDimension(renderingConfig)
    }
  }

  test("nested child offsets are applied exactly once") {
    val child = rectangle(10, 8)
    val inner = AppShapeComposition(CompositionVBox[Double](AlignmentInParent.TopLeft), shapeConfig, List(child))
    val outer = AppShapeComposition(CompositionHBox[Double](AlignmentInParent.TopLeft), shapeConfig, List(inner))

    val renderedChild = outer.renderWithMinimumDimension(renderingConfig).children.head.children.head

    assertEquals(renderedChild.myBounds.startPoint, Point(6.0, 9.0))
    assertEquals(renderedChild.myBounds.dimension, Dimension(10.0, 8.0))
  }

  test("horizontal and vertical compositions include gaps and align children") {
    val horizontal = AppShapeComposition(
      CompositionHBox[Double](AlignmentInParent.BottomLeft),
      shapeConfig,
      List(rectangle(10, 8), rectangle(20, 4))
    ).renderWithMinimumDimension(renderingConfig)
    assertEquals(horizontal.myBounds.dimension, Dimension(39.0, 11.0))
    assertEquals(horizontal.children.map(_.myBounds.startPoint), List(Point(4.0, 6.0), Point(21.0, 10.0)))

    val vertical = AppShapeComposition(
      CompositionVBox[Double](AlignmentInParent.TopRight),
      shapeConfig,
      List(rectangle(10, 8), rectangle(20, 4))
    ).renderWithMinimumDimension(renderingConfig)
    assertEquals(vertical.myBounds.dimension, Dimension(22.0, 25.0))
    assertEquals(vertical.children.map(_.myBounds.startPoint), List(Point(14.0, 6.0), Point(4.0, 24.0)))
  }

  test("ratio-preserving atomic shapes align within requested content bounds") {
    val circle = AppShapeDrawingRoutineElement(CircleShape[Double](), shapeConfig, Some(Dimension(10.0, 10.0)))
    val rendered = circle.renderComposition(
      renderingConfig,
      Bounds(Point(0.0, 0.0), Dimension(202.0, 103.0))
    )

    assertEquals(rendered.myBounds, Bounds(Point(52.0, 3.0), Dimension(100.0, 100.0)))
  }

  test("grid rejects a child count that differs from its cell count") {
    val alignments: Matrix[AlignmentInParent] = Matrix(2, 1, _ => AlignmentInParent.TopLeft)
    val grid = AppShapeComposition(CompositionGrid[Double](alignments), shapeConfig, List(rectangle(10, 10)))

    intercept[IllegalArgumentException](grid.renderWithMinimumDimension(renderingConfig))
  }
}
