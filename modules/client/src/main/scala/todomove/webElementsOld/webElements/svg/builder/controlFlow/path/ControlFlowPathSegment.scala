package todomove.webElementsOld.webElements.svg.builder.controlFlow.path

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilder
import todomove.webElementsOld.webElements.svg.builder.controlFlow.ControlFlowOverlayElement
import todomove.webElementsOld.webElements.svg.shapes.BeShape

case class ControlFlowPathSegment(curPath: SvgPathBuilder[Double], segmentType: SegmentType) extends ControlFlowOverlayElement() {
  def toShape(renderingConfig: BeRenderingConfig): BeShape = {
    curPath.toFixedDimensionShape.addAmends(renderingConfig.controlFlowAmendMap(segmentType))
  }
}
