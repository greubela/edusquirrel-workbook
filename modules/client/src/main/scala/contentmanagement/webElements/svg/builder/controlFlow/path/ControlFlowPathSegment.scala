package contentmanagement.webElements.svg.builder.controlFlow.path

import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.builder.controlFlow.ControlFlowOverlayElement
import contentmanagement.webElements.svg.shapes.BeShape
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class ControlFlowPathSegment(curPath: SvgPathBuilder[Double], segmentType: SegmentType) extends ControlFlowOverlayElement() {
  def toShape(renderingConfig: BeRenderingConfig): BeShape = {
    curPath.toFixedDimensionShape.addAmends(renderingConfig.controlFlowAmendMap(segmentType))
  }
}
