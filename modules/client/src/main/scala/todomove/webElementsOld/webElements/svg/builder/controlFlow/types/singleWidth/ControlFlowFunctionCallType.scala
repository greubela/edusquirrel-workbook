package todomove.webElementsOld.webElements.svg.builder.controlFlow.types.singleWidth

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.controlFlow.path.ControlFlowPathOverlay

case class ControlFlowFunctionCallType() extends ControlFlowTypeSingleWidth {

  private val ref = ControlFlowDirectedType(true, true)

  override def minHeightInSegments: Int = ref.minHeightInSegments

  override def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay = {
    ref.renderPaths(renderingConfig, oldOverlay)
  }
}
