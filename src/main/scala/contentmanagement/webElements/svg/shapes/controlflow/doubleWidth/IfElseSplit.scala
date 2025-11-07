package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.shapes.*
import contentmanagement.webElements.svg.shapes.composite.VerticalAlignment.Top
import contentmanagement.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import IfElseSplit.{IfElseConditionOverlay, LeftPathFalseOverlay, MoveControlFlowToCenter, RightPathTrueOverlay}
import contentmanagement.webElements.svg.shapes.controlflow.overlays.BeDataArrow
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.ControlFlowShapeDoubleWidth
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class IfElseSplit() extends ControlFlowShapeDoubleWidth {

  override def continuesWithoutInterruption: Boolean = false

  override def minHeightInSegments: Int = 8

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (false, true)))

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {

    val bwa = background.addAmends(rendererConfig.amendFactory.defaultControlColors)
    val mwa = MoveControlFlowToCenter().addAmends(rendererConfig.amendFactory.activeControlFlowAmends)
    val rwa = RightPathTrueOverlay().addAmends(rendererConfig.amendFactory.trueConditionControlFlowAmends)
    val lwa = LeftPathFalseOverlay().addAmends(rendererConfig.amendFactory.falseConditionControlFlowAmends)
    val twa = IfElseConditionOverlay().addAmends(rendererConfig.amendFactory.splitSymbolControlFlowAmends)

    val awa = BeDataArrow().addAmends(rendererConfig.amendFactory.activeDecorationElements)

    val noOffset: Point[Double] = Point[Double](0, 0)
    val overlays: List[BeShape] = List(bwa, mwa, rwa, lwa, awa)

    val arrowLeft = rendererConfig.controlSegmentSize * 9.0
    val arrowTop = rendererConfig.controlSegmentSize * 3.0 + rendererConfig.controlSegmentSize / 5.0 * 1.0

    val stack = ShapeStack(List(lwa, rwa, mwa, twa, awa), HorizontalAlignment.Left, VerticalAlignment.Top, Map(awa -> Point[Double](arrowLeft, arrowTop)))

    val bR = bwa.render(rendererConfig, bounds)
    val sR = stack.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List())

  }
  
}

object IfElseSplit {

  case class MoveControlFlowToCenter() extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(3 * seg, 0))
        .lineToRel(Dimension(0, 2 * seg))
        .lineToRel(Dimension(3 * seg, 0))
        .lineToRel(Dimension(0, seg))
    }
  }

  case class IfElseConditionOverlay() extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(6 * seg, 3 * seg))
        .lineToRel(Dimension(seg, seg))
        .lineToRel(Dimension(-seg, seg))
        .lineToRel(Dimension(-seg, -seg))
        .lineToRel(Dimension(seg, -seg))
        .closePath()
    }
  }

  case class RightPathTrueOverlay() extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(6 * seg, 4 * seg))
        .lineToRel(Dimension(3 * seg, 3 * seg))
         .lineToRel(Dimension(0, seg))
    }
  }

  case class LeftPathFalseOverlay() extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(6 * seg, 4 * seg))
        .lineToRel(Dimension(-3 * seg, 3 * seg))
         .lineToRel(Dimension(0, seg))
    }
  }


}
