package contentmanagement.webElements.svg.builder.controlFlow.path

import contentmanagement.model.geometry.Point
import contentmanagement.webElements.svg.builder.controlFlow.ControlFlowOverlayElement
import contentmanagement.webElements.svg.shapes.BeShape
import contentmanagement.webElements.svg.shapes.composite.{BoxManualPositioning, ManualPositionElement}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig


case class ControlFlowPath(curStatus: PathStatus, pathType: PathType, segments: List[ControlFlowPathSegment]) extends ControlFlowOverlayElement {
  def toShape(renderingConfig: BeRenderingConfig): BeShape = new BoxManualPositioning() {
    override def calcOffsetsAndDimensions(config: BeRenderingConfig): List[ManualPositionElement] = {
      segments.map(curSegment => ManualPositionElement(curSegment.toShape(renderingConfig), Point[Double](0, 0), curSegment.toShape(renderingConfig).displaySize(config)))
    }
  }
}
