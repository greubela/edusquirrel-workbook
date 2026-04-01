package contentmanagement.webElements.svg.shapes.controlflow.singleWidth

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeDecoration}
import contentmanagement.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowDirected.*
import contentmanagement.webElements.svg.shapes.controlflow.*
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.ControlFlowProgramStopper.*
import contentmanagement.webElements.svg.shapes.decorations.TriangleOverlay
import datastructures.core.geometry.{Bounds, Dimension, Point}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathSegment
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus.FINISHED

case class ControlFlowProgramStopper() extends ControlFlowShapeSingleWidth {

  override def background: BeShape.BeShapeContainerable = ControlFlowStopperBackground()

  override def minHeightInSegments: Int = 3

  /*
  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val backgroundWithAmends = background.addAmends(rendererConfig.amendFactory.defaultControlColors)

    val lineDownWithAmends = ControlFlowLineVertical(0, bounds.height - rendererConfig.controlSegmentSize).addAmends(rendererConfig.amendFactory.activeControlFlowAmends)
    val lineEndWithAmends = ControlFlowLineEnd(bounds.height - rendererConfig.controlSegmentSize).addAmends(rendererConfig.amendFactory.activeControlFlowAmends)

    val stack = ShapeStack(List(lineDownWithAmends, lineEndWithAmends), HorizontalAlignment.Left, VerticalAlignment.Top, Map())
    val sR = stack.render(rendererConfig, bounds)

    val bR = backgroundWithAmends.render(rendererConfig, bounds)
    AppDecoratedSvgElement(bR, List(sR), List()).addMods(List(svg.cls := "ControlFlowProgramStarter"))
  }
*/
  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    val seg = renderingInfo.renderingConfig.controlSegmentSize
    val actualLineHeight = curLineHeight - renderingInfo.renderingConfig.controlSegmentSize / 2
    cf
      .changeFirstOpenPath(_
        .changeLastPathBuilder(_
          .lineToRel(Dimension(0, actualLineHeight / 2))
          .moveToRel(Dimension(-seg, 0))
          .horizontalLineWithWidth(2 * seg)
        )
        .copy(curStatus = FINISHED)
      )
  }

}

object ControlFlowProgramStopper {
  /*
    case class ControlFlowLineEnd(y: Double) extends BeShapeDecoration {
      override def getOverlayPath(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = {
        val seg = rendererConfig.controlSegmentSize
        SvgPathBuilder(bounds.startPoint)
          .moveToRel(Dimension(2 * seg, y))
          .horizontalLineWithWidth(2 * seg)
      }
    }
  */
}
