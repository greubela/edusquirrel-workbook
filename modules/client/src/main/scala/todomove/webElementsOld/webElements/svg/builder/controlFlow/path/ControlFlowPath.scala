package todomove.webElementsOld.webElements.svg.builder.controlFlow.path

import it.evadid.core.datastructures.geometry.Point
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.controlFlow.ControlFlowOverlayElement
import todomove.webElementsOld.webElements.svg.shapes.BeShape
import todomove.webElementsOld.webElements.svg.shapes.composite.{BoxManualPositioning, ManualPositionElement}


case class ControlFlowPath(curStatus: PathStatus, pathType: PathType, segments: List[ControlFlowPathSegment]) extends ControlFlowOverlayElement {
  def toShape(renderingConfig: BeRenderingConfig): BeShape = new BoxManualPositioning() {
    override def calcOffsetsAndDimensions(config: BeRenderingConfig): List[ManualPositionElement] = {
      segments.map(curSegment => ManualPositionElement(curSegment.toShape(renderingConfig), Point[Double](0, 0), curSegment.toShape(renderingConfig).displaySize(config)))
    }
  }
}
