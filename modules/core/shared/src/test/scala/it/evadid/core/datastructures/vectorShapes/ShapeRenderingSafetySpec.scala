package it.evadid.core.datastructures.vectorShapes

import it.evadid.core.datastructures.color.RGBColor
import it.evadid.core.datastructures.font.AppFont
import it.evadid.core.datastructures.geometry.{AspectRatio, Bounds, Dimension, Point}
import it.evadid.core.datastructures.matrix.Matrix
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.{AppElementRendered, AppShapeComposition}
import it.evadid.core.datastructures.vectorShapes.atomar.AppShapeDrawingRoutineElement
import it.evadid.core.datastructures.vectorShapes.compositions.*
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.helper.{AlignmentInParent, RenderingDimension}
import it.evadid.core.datastructures.vectorShapes.renderer.SvgViewport
import it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines.{CircleShape, RectangleShape}
import munit.FunSuite

class ShapeRenderingSafetySpec extends FunSuite {
  private val rendering = AppShapeRenderingConfig.defaultDouble
  private def config(stroke: Double = 0, padding: Option[Dimension[Double]] = None): AppShapeElementConfig[Double] =
    new AppShapeElementConfig[Double] {
      override def useCustomPadding = padding
      override def strokeWidth = stroke
      override def font = AppFont.defaultFont
      override def colorFill = RGBColor.red
      override def colorStroke = RGBColor.black
      override def colorFont = RGBColor.black
      override def onMouseClicked(leftButton: Boolean): Unit = ()
    }
  private def rectangle(w: Double, h: Double, stroke: Double = 0) =
    AppShapeDrawingRoutineElement(RectangleShape[Double](), config(stroke), Some(Dimension(w, h)))

  private def assertContained(parent: Bounds[Double], child: Bounds[Double]): Unit = {
    assert(child.startX >= parent.startX && child.startY >= parent.startY, clue = s"$child outside $parent")
    assert(child.endX <= parent.endX && child.endY <= parent.endY, clue = s"$child outside $parent")
  }

  private def assertChildrenContained(node: AppElementRendered[Double]): Unit =
    node.children.foreach { child =>
      assertContained(node.myBounds, child.outerBounds)
      assertChildrenContained(child)
    }

  test("all compositions reject undersized targets and contain children at minimum or larger sizes") {
    val grid = Matrix[AlignmentInParent](2, 1, _ => AlignmentInParent.BottomRight)
    val controls: List[AppShapeCompositeControl[Double]] = List(
      CompositionHBox(AlignmentInParent.BottomRight), CompositionVBox(AlignmentInParent.BottomRight),
      CompositionBlockStack(AlignmentInParent.BottomRight), CompositionGrid(grid)
    )
    controls.foreach { control =>
      val shape = AppShapeComposition(control, config(), List(rectangle(100, 80), rectangle(40, 30)))
      List(Dimension(20.0, 1000.0), Dimension(1000.0, 20.0)).foreach { target =>
        intercept[IllegalArgumentException](shape.renderComposition(rendering, Bounds(Point(10.0, 20.0), target)))
      }
      assertChildrenContained(shape.renderWithMinimumDimension(rendering))
      assertChildrenContained(shape.renderComposition(rendering, Bounds(Point(10.0, 20.0), Dimension(1000.0, 1000.0))))
    }
  }

  test("nested compositions preserve allocated bounds and reserve padding only once") {
    val inner = AppShapeComposition(CompositionVBox[Double](AlignmentInParent.TopLeft), config(), List(rectangle(10, 20)))
    val outer = AppShapeComposition(CompositionHBox[Double](AlignmentInParent.TopLeft), config(), List(inner))
    val rendered = outer.renderWithMinimumDimension(rendering)
    assertChildrenContained(rendered)
    assertEquals(rendered.children.head.children.head.myBounds.startPoint, Point(6.0, 9.0))
    assertEquals(SvgViewport.boundsFor(rendered), rendered.outerBounds)
  }

  test("viewport preserves explicit allocation and aspect-ratio alignment") {
    val target = Bounds(Point(-100.0, 200.0), Dimension(202.0, 103.0))
    val circle = AppShapeDrawingRoutineElement(CircleShape[Double](), config(), Some(Dimension(10.0, 10.0)))
      .renderComposition(rendering, target)
    assertEquals(circle.outerBounds, target)
    assertEquals(circle.myBounds, Bounds(Point(-48.0, 203.0), Dimension(100.0, 100.0)))
    assertEquals(SvgViewport.boundsFor(circle), target)
  }

  test("viewport includes descendant stroke extents even with zero padding") {
    val zeroPadding = rendering.copy(defaultPadding = Dimension(0.0, 0.0))
    val shape = AppShapeComposition(CompositionHBox[Double](AlignmentInParent.TopLeft), config(),
      List(rectangle(10, 20, stroke = 12), rectangle(20, 10, stroke = 2)))
      .renderWithMinimumDimension(zeroPadding)
    val viewport = SvgViewport.boundsFor(shape)
    assertContained(viewport, shape.outerBounds)
    shape.children.foreach { child =>
      val radius = child.elementConfig.strokeWidth * 2 // Default SVG miter limit.
      assertContained(viewport, Bounds(Point(child.myBounds.startX - radius, child.myBounds.startY - radius),
        child.myBounds.dimension.increaseSize(radius * 2, radius * 2)))
    }
  }

  test("dimensions, padding, gaps and origins reject negative or non-finite geometry") {
    List(-1.0, Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity).foreach { invalid =>
      val bad = Dimension(invalid, 10.0)
      intercept[IllegalArgumentException](RenderingDimension(bad, bad))
      intercept[IllegalArgumentException](RenderingDimension.fromRawDimensionAndConfig(bad, config(), rendering))
      intercept[IllegalArgumentException](RenderingDimension.fromFullDimensionAndConfig(bad, config(), rendering))
      intercept[IllegalArgumentException](rectangle(10, 20).renderWithMinimumDimension(rendering.copy(defaultPadding = bad)))
      intercept[IllegalArgumentException](rectangle(10, 20).renderWithMinimumDimension(rendering.copy(gapBetweenConsecutiveShapes = bad)))
      intercept[IllegalArgumentException](RenderingDimension.fromRawDimensionAndConfig(Dimension(10.0, 20.0), config(padding = Some(bad)), rendering))
    }
    List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity).foreach { invalid =>
      intercept[IllegalArgumentException](rectangle(1, 1).renderComposition(rendering,
        Bounds(Point(invalid, 0.0), Dimension(10.0, 10.0))))
    }
    intercept[IllegalArgumentException](RenderingDimension(Dimension(20.0, 20.0), Dimension(10.0, 10.0)))
    intercept[IllegalArgumentException](RenderingDimension.fromRawDimensionAndConfig(
      Dimension(Double.MaxValue, 10.0), config(padding = Some(Dimension(Double.MaxValue, 0.0))), rendering))
  }

  test("both fitting overloads reject invalid inputs and aspect ratios") {
    List(-1.0, Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity).foreach { invalid =>
      val bad = Dimension(10.0, invalid)
      intercept[IllegalArgumentException](AppShapeCompositeControl.calculateAdjustedDimension(bad, None))
      intercept[IllegalArgumentException](AppShapeCompositeControl.calculateAdjustedDimension(
        bad, Some(Dimension(1.0, 1.0)), AlignmentInParent.MiddleCenter, true))
      intercept[IllegalArgumentException](AppShapeCompositeControl.calculateAdjustedDimension(
        Dimension(10.0, 10.0), Some(bad), AlignmentInParent.MiddleCenter, true))
    }
    List(0.0, -1.0, Double.NaN, Double.PositiveInfinity).foreach { ratio =>
      intercept[IllegalArgumentException](AppShapeCompositeControl.calculateAdjustedDimension(
        Dimension(10.0, 10.0), Some(AspectRatio(ratio) -> AlignmentInParent.MiddleCenter)))
    }
  }

  test("zero-sized and one-dimensional fitting stays finite without losing an unconstrained axis") {
    val target = Dimension(0.0, 20.0)
    assertEquals(AppShapeCompositeControl.calculateAdjustedDimension(target, None), target)
    assertEquals(AppShapeCompositeControl.calculateAdjustedDimension(target,
      Some(AspectRatio(1.0) -> AlignmentInParent.MiddleCenter)), Dimension(0.0, 0.0))
    List(Dimension(0.0, 0.0) -> Dimension(0.0, 0.0),
      Dimension(0.0, 5.0) -> Dimension(0.0, 20.0),
      Dimension(5.0, 0.0) -> Dimension(10.0, 0.0)).foreach { (desired, expected) =>
      assertEquals(AppShapeCompositeControl.calculateAdjustedDimension(Dimension(10.0, 20.0),
        Some(desired), AlignmentInParent.MiddleCenter, true), expected)
    }
  }

  test("invalid stroke widths fail before producing SVG geometry") {
    List(-1.0, Double.NaN, Double.PositiveInfinity).foreach { stroke =>
      intercept[IllegalArgumentException](SvgViewport.boundsFor(rectangle(10, 20, stroke).renderWithMinimumDimension(rendering)))
      val composition = AppShapeComposition(CompositionHBox[Double](AlignmentInParent.TopLeft), config(stroke),
        List(rectangle(10, 20))).renderWithMinimumDimension(rendering)
      intercept[IllegalArgumentException](SvgViewport.boundsFor(composition))
    }
  }
}
