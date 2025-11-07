package contentmanagement.webElements.svg.shapes.controlflow.singleWidth

import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class ControlFlowFunctionCall() extends ControlFlowShapeSingleWidth {

  val ref = ControlFlowDirected(true, true)

  override def continuesWithoutInterruption: Boolean = ref.continuesWithoutInterruption

  override def minHeightInSegments: Int = ref.minHeightInSegments

  override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement =
    ref.render(rendererConfig, bounds)
}
