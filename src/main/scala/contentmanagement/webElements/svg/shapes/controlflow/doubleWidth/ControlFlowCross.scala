package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import ControlFlowCross.{LeftToRight, PathCrossOverlay, RightToLeft}
import IfElseUnion.{LeftPathToCenter, MoveControlFlowToLeft, PathUnionOverlay, RightPathToCenter}
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.ControlFlowShapeDoubleWidth
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeDecoration}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class ControlFlowCross() extends ControlFlowShapeDoubleWidth {

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (true, true)))

  override def continuesWithoutInterruption: Boolean = false

  override def minHeightInSegments: Int = 8

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {

    val bwa = background.addAmends(rendererConfig.amendFactory.defaultControlColors)
    val rwa = RightToLeft().addAmends(rendererConfig.amendFactory.activeControlFlowAmends)
    val lwa = LeftToRight().addAmends(rendererConfig.amendFactory.inactiveControlFlowAmends)
    val pwa = PathCrossOverlay().addAmends(rendererConfig.amendFactory.crossSymbolControlFlowAmends)

    val stack = ShapeStack(List(lwa, rwa, pwa), HorizontalAlignment.Left, VerticalAlignment.Top)

    val bR = bwa.render(rendererConfig, bounds)
    val sR = stack.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List())
  }
}
object ControlFlowCross {

  case class RightToLeft() extends BeShapeDecoration{
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(9 * seg, 0))
        .lineToRel(Dimension(0, seg))
        .lineToRel(Dimension(-6 * seg, 6 * seg))
        .lineToRel(Dimension(0, seg))
    }
  }

  case class LeftToRight() extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(3 * seg, 0))
        .lineToRel(Dimension(0, seg))
        .lineToRel(Dimension(6 * seg, 6 * seg))
        .lineToRel(Dimension(0, seg))
    }
  }

  case class PathCrossOverlay() extends BeShapeDecoration {
    override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
      val seg = rendererConfig.controlSegmentSize
      SvgPathBuilder(bounds.startPoint)
        .moveToRel(Dimension(6 * seg, 4 * seg))
        .addCenteredCircle(seg)
        .closePath()
    }
  }


}