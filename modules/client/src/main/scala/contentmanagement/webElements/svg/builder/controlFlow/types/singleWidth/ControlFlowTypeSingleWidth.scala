package contentmanagement.webElements.svg.builder.controlFlow.types.singleWidth

import contentmanagement.webElements.svg.builder.controlFlow.ControlFlowType
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeContainerable
import contentmanagement.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground

abstract class ControlFlowTypeSingleWidth extends ControlFlowType {

  override def widthInIndentationLevels: Int = 1

  override def backgroundShape: BeShapeContainerable =
    ControlFlowConnectorBackground(List((true, true)), true)
}
