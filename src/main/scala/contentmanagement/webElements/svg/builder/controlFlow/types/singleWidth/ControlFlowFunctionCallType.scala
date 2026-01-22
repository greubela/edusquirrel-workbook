package contentmanagement.webElements.svg.builder.controlFlow.types.singleWidth

import contentmanagement.webElements.svg.builder.controlFlow.path.ControlFlowPathOverlay
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class ControlFlowFunctionCallType() extends ControlFlowTypeSingleWidth {

  private val ref = ControlFlowDirectedType(true, true)

  override def minHeightInSegments: Int = ref.minHeightInSegments

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    ref.renderPaths(renderingConfig, oldOverlay)
  }
}
