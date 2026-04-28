package contentmanagement.webElements.svg.shapes.controlflow.singleWidth

import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder
import it.evadid.core.datastructures.geometry.{Bounds, Point}

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
