package contentmanagement.webElements.svg.shapes.controlflow.singleWidth

import contentmanagement.model.geometry.Bounds
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

abstract class ControlFlowShapeSingleWidth extends ControlFlowShape {

  override def widthInIntendations: Int = 1

  override def background: BeShape.BeShapeContainerable = ControlFlowConnectorBackground(List((true, true)))

}
