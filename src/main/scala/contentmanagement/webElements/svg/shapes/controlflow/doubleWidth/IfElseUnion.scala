package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import IfElseUnion.{LeftPathToCenter, MoveControlFlowToLeft, PathUnionOverlay, RightPathToCenter}
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.ControlFlowShapeDoubleWidth
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeDecoration}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class IfElseUnion() extends ControlFlowShapeDoubleWidth {


  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (true, false)))

  override def continuesWithoutInterruption: Boolean = false

  override def minHeightInSegments: Int = 7

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {

    val bwa = background.addAmends(rendererConfig.amendFactory.defaultControlColors)
    val rwa = RightPathToCenter().addAmends(rendererConfig.amendFactory.activeControlFlowAmends)
    val lwa = LeftPathToCenter().addAmends(rendererConfig.amendFactory.inactiveControlFlowAmends)
    val mwa = MoveControlFlowToLeft().addAmends(rendererConfig.amendFactory.activeControlFlowAmends)
    val pwa = PathUnionOverlay().addAmends(rendererConfig.amendFactory.combineSymbolControlFlowAmends)

    val stack = ShapeStack(List(lwa, rwa, mwa, pwa), HorizontalAlignment.Left, VerticalAlignment.Top)

    val bR = bwa.render(rendererConfig, bounds)
    val sR = stack.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List())
  }


}

object IfElseUnion {

  case class RightPathToCenter() extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(9 * seg, 0))
        .lineToRel(Dimension(0, seg))
        .lineToRel(Dimension(-3 * seg, 3 * seg))
    }
  }

  case class LeftPathToCenter() extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(3 * seg, 0))
        .lineToRel(Dimension(0, seg))
        .lineToRel(Dimension(3 * seg, 3 * seg))
    }
  }

  case class MoveControlFlowToLeft() extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(6 * seg, 5 * seg))
        .lineToRel(Dimension(0, seg))
        .lineToRel(Dimension(-3 * seg, 0))
        .lineToRel(Dimension(0, seg))
    }
  }

  case class PathUnionOverlay() extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(6 * seg, 4 * seg))
        .addCenteredCircle(seg)
        .closePath()
    }
  }

}