package it.evadid.core.datastructures.vectorShapes

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.helper.{AlignmentInParent, RenderingDimension}
import munit.FunSuite

class ShapeLayoutHelpersTest extends FunSuite {
  private val container = Dimension(100.0, 80.0)
  private val child = Dimension(20.0, 10.0)

  test("all positional alignments calculate the expected offsets") {
    val expected = List(
      AlignmentInParent.TopLeft -> Point(0.0, 0.0),
      AlignmentInParent.TopCenter -> Point(40.0, 0.0),
      AlignmentInParent.TopRight -> Point(80.0, 0.0),
      AlignmentInParent.MiddleLeft -> Point(0.0, 35.0),
      AlignmentInParent.MiddleCenter -> Point(40.0, 35.0),
      AlignmentInParent.MiddleRight -> Point(80.0, 35.0),
      AlignmentInParent.BottomLeft -> Point(0.0, 70.0),
      AlignmentInParent.BottomCenter -> Point(40.0, 70.0),
      AlignmentInParent.BottomRight -> Point(80.0, 70.0)
    )

    expected.foreach { (alignment, offset) =>
      assertEquals(AppShapeCompositeControl.calculateOffset(container, child, alignment), offset)
    }
    assertEquals(
      AppShapeCompositeControl.calculateOffset(container, child, AlignmentInParent.DistortionAlignment),
      Point(0.0, 0.0)
    )
  }

  test("an oversized aligned child is never assigned a negative offset") {
    val offset = AppShapeCompositeControl.calculateOffset(
      Dimension(10.0, 10.0),
      Dimension(20.0, 30.0),
      AlignmentInParent.BottomRight
    )

    assertEquals(offset, Point(0.0, 0.0))
  }

  test("aspect-ratio fitting preserves ratio and alignment") {
    val relative = AppShapeCompositeControl.calculateRelativeBounds(
      Dimension(200.0, 100.0),
      Some(AspectRatio(1.0) -> AlignmentInParent.MiddleCenter)
    )

    assertEquals(relative.offsetInParents, Point(50.0, 0.0))
    assertEquals(relative.dimension, Dimension(100.0, 100.0))
  }

  test("aspect-ratio fitting handles empty target dimensions without NaN") {
    val relative = AppShapeCompositeControl.calculateRelativeBounds(
      Dimension(0.0, 100.0),
      Some(AspectRatio(1.0) -> AlignmentInParent.MiddleCenter)
    )

    assertEquals(relative.offsetInParents, Point(0.0, 50.0))
    assertEquals(relative.dimension, Dimension(0.0, 0.0))
  }

  test("rendering dimensions round-trip between raw and full sizes") {
    val config = AppShapeRenderingConfig.defaultDouble.copy(defaultPadding = Dimension(4.0, 6.0))
    val elementConfig = AppShapeElementConfig.EvaShapeConfigDefault[Double]
    val fromRaw = RenderingDimension.fromRawDimensionAndConfig(Dimension(20.0, 30.0), elementConfig, config)
    val fromFull = RenderingDimension.fromFullDimensionAndConfig(fromRaw.fullDimension, elementConfig, config)

    assertEquals(fromRaw, RenderingDimension(Dimension(20.0, 30.0), Dimension(24.0, 36.0)))
    assertEquals(fromFull, fromRaw)
  }
}
