package contentmanagement.webElements.svg.shapes.controlflow.doubleWidth

import com.raquo.laminar.api.L
import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import contentmanagement.webElements.svg.shapes.controlflow.doubleWidth.ControlFlowCross.*
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeDecoration}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.controlflow.decorations.PathCrossOverlay
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.{ControlFlowPath, PathSegment}

case class ControlFlowCross() extends ControlFlowShapeDoubleWidth {

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true), (true, true)))

  override def minHeightInSegments: Int = 8

  /*
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
*/

  private def extendAndAppendParentPath(path: ControlFlowPath, curLineHeight: Double, seg: Double, toTheRight: Boolean, newPathAmends: Seq[L.Modifier[L.SvgElement]]): ControlFlowPath = {
    val extraHeight = (curLineHeight - seg * 6).max(0)
    val xDirection = if (toTheRight) 3 * seg else -3 * seg

    path
      .changeLastPathBuilder(curPathBuilder => {
        curPathBuilder
          .verticalLineWithHeight(extraHeight / 2)
          .lineToRel(Dimension[Double](xDirection, 3.0 * seg))
      })
      .appendNewSegmentWithLastSegmentPosition(position => {
        val path = SvgPathBuilder[Double](position)
          .lineToRel(Dimension[Double](xDirection, 3.0 * seg))
          .verticalLineWithHeight(extraHeight / 2)
        PathSegment(path, List(), true, newPathAmends)
      })
  }

  private def handleFirstParentPath(path: ControlFlowPath, renderingConfig: BeRenderingConfig, curLineHeight: Double): ControlFlowPath = {
    extendAndAppendParentPath(path, curLineHeight, renderingConfig.controlSegmentSize, true, renderingConfig.amendFactory.falseConditionControlFlowAmends)
  }

  private def handleSecondParentPath(path: ControlFlowPath, renderingConfig: BeRenderingConfig, curLineHeight: Double): ControlFlowPath = {
    extendAndAppendParentPath(path, curLineHeight, renderingConfig.controlSegmentSize, false, renderingConfig.amendFactory.inactiveControlFlowAmends)
  }

  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize
    cf
      .changeFirstOpenPath(handleFirstParentPath(_, renderingInfo.renderingConfig, curLineHeight))
      .changeFirstOpenPath(handleSecondParentPath(_, renderingInfo.renderingConfig, curLineHeight))
      .addDecoration(PathCrossOverlay(), centerPoint)
  }


}

object ControlFlowCross {


}