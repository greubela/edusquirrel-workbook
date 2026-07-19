package todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import ControlFlowDirected.*
import todomove.webElementsOld.webElements.svg.shapes.controlflow.*
import ControlFlowProgramStopper.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathSegment
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.PathStatus.FINISHED
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.compositeElements.AppDecoratedSvgElement
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, BeShapeDecoration}
import todomove.webElementsOld.webElements.svg.shapes.composite.{HorizontalAlignment, ShapeStack, VerticalAlignment}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.ControlFlowStopperBackground
import todomove.webElementsOld.webElements.svg.shapes.decorations.TriangleOverlay

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
