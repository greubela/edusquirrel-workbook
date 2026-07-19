package it.evadid.core.datastructures.vectorShapes

import munit.FunSuite
import it.evadid.core.datastructures.color.{AppColor, RGBYColorPalette}
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.vectorShapes.abstractions.AlignmentInParent
import it.evadid.core.datastructures.vectorShapes.compositions.{CompositionHBox, CompositionVBox}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.rendering.{AppCompositionDimensioned, AppShapeComposition}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath
import it.evadid.core.datastructures.vectorShapes.svg.drawingRoutines.*
import it.evadid.util.logging.BasicLogger

class VectorShapesTest extends FunSuite {
  private val palette = new RGBYColorPalette {
    override def grayscale: List[AppColor] = Nil
    override def reds: List[AppColor] = Nil
    override def greens: List[AppColor] = Nil
    override def blues: List[AppColor] = Nil
    override def yellows: List[AppColor] = Nil
  }
  private val renderingConfig = AppShapeRenderingConfig[Double](BasicLogger(), "test", AppLanguage.English, palette, Dimension(0, 0), Dimension(3, 4))
  private val shapeConfig = AppShapeConfig.EvaShapeConfigDefault(renderingConfig)

  private def dimensioned(width: Double, height: Double): AppCompositionDimensioned[Double] = {
    val composition = AppShapeComposition(CompositionHBox[Double](AlignmentInParent.TopLeft), shapeConfig, Nil)
    AppCompositionDimensioned(composition, shapeConfig, renderingConfig, Dimension(width, height))
  }

  test("relative rectangle uses width for x and height for y") {
    val path = RectangleShape[Double]().renderPath(BasicLogger(), Point(10, 20), Dimension(100, 50), AlignmentInParent.DistortionAlignment)
    assertEquals(path.svgPathDString, "M 10 20 l 100 0 l 0 50 l -100 0 l 0 -50 Z")
  }

  test("ratio-preserving drawings align within their requested bounds") {
    val path = CircleShape[Double]().renderPath(BasicLogger(), Point(0, 0), Dimension(200, 100), AlignmentInParent.MiddleCenter)
    assert(path.svgPathDString.startsWith("M 50 0"), path.svgPathDString)
  }

  test("all migrated reporter routines create closed SVG paths") {
    val routines = List(BooleanShape[Double](), NumericShape[Double](), StringShape[Double](), DateShape[Double](), LiteralShape[Double](), CommandShape[Double](), DuckShape[Double]())
    routines.foreach { routine =>
      val path = routine.renderPath(BasicLogger(), Point(0, 0), Dimension(120, 40), AlignmentInParent.DistortionAlignment)
      assert(path.svgPathDString.endsWith(" Z"), path.svgPathDString)
    }
  }

  test("Snap drawing routines are explicitly named and create closed SVG paths") {
    val routines = List(SnapCommandShape[Double](), SnapCShape[Double](), SnapHatShape[Double](), SnapReporterShape[Double](), SnapBooleanShape[Double]())
    routines.foreach { routine =>
      val path = routine.renderPath(BasicLogger(), Point(0, 0), Dimension(120, 60), AlignmentInParent.DistortionAlignment)
      assert(path.svgPathDString.endsWith(" Z"), path.svgPathDString)
    }
  }

  test("horizontal composition includes gaps and vertically aligns children") {
    val control = CompositionHBox[Double](AlignmentInParent.BottomLeft)
    val children = List(dimensioned(10, 8), dimensioned(20, 4))
    assertEquals(control.dimensionControl.calculateRawMinimumDimension(renderingConfig, children), Dimension(33.0, 8.0))
    assertEquals(control.positionControl.calculateChildrenOffsets(renderingConfig, children).map(_.compositionOffset.relativeOffset), List(Point(0.0, 0.0), Point(13.0, 4.0)))
  }

  test("vertical composition includes gaps and horizontally aligns children") {
    val control = CompositionVBox[Double](AlignmentInParent.TopRight)
    val children = List(dimensioned(10, 8), dimensioned(20, 4))
    assertEquals(control.dimensionControl.calculateRawMinimumDimension(renderingConfig, children), Dimension(20.0, 16.0))
    assertEquals(control.positionControl.calculateChildrenOffsets(renderingConfig, children).map(_.compositionOffset.relativeOffset), List(Point(10.0, 0.0), Point(0.0, 12.0)))
  }
}
