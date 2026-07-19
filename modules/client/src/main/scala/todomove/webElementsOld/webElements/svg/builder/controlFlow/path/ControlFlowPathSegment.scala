package todomove.webElementsOld.webElements.svg.builder.controlFlow.path

import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.SvgBridge
import todomove.webElementsOld.webElements.svg.builder.controlFlow.ControlFlowOverlayElement
import todomove.webElementsOld.webElements.svg.shapes.BeShape

case class ControlFlowPathSegment(curPath: SvgPathBuilder[Double], segmentType: SegmentType) extends ControlFlowOverlayElement() {
  def toShape(renderingConfig: BeRenderingConfig): BeShape = {
    SvgBridge.toFixedDimensionShape(curPath).addAmends(renderingConfig.controlFlowAmendMap(segmentType))
  }
}
