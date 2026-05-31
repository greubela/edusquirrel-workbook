package todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth

import it.evadid.core.datastructures.geometry.{Bounds, Point}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import todomove.webElementsOld.webElements.svg.AppSvgElement

case class ControlFlowFunctionCall() extends ControlFlowShapeSingleWidth {

  val ref = ControlFlowDirected(true, true)

  override def minHeightInSegments: Int = ref.minHeightInSegments

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    ref.render(rendererConfig, bounds)
  }

  override def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder = {
    ref.renderControlFlow(cf, renderingInfo, centerPoint, curLineHeight)
  }
  
}
