package todomove.webElementsOld.webElements.svg.builder.controlFlow.types.singleWidth

import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapeContainerable
import todomove.webElementsOld.webElements.svg.builder.controlFlow.ControlFlowType
import todomove.webElementsOld.webElements.svg.shapes.controlflow.ControlFlowConnectorBackground

abstract class ControlFlowTypeSingleWidth extends ControlFlowType {

  override def widthInIndentationLevels: Int = 1

  override def backgroundShape: BeShapeContainerable =
    ControlFlowConnectorBackground(List((true, true)), true)
}
