package interactionPlugins.blockEnvironment.programming.blockdisplay.use

import datastructures.core.vm.types.BeChildRole.ValueInAssignment
import contentmanagement.webElements.svg.shapes.{BeShape, ControlFlowShape}
import contentmanagement.webElements.svg.shapes.composite.{HBoxSameHeight, ShapeAroundShape}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.{ControlFlowDirected, ControlFlowFunctionCall}
import contentmanagement.webElements.svg.shapes.datatypes.UnitShape
import contentmanagement.webElements.svg.shapes.decorations.BeDataArrow
import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.code.defining.BeDefineVariable
import datastructures.core.vm.code.tree.BeExpressionNode
import datastructures.core.vm.code.usage.BeUseValue
import interactionPlugins.blockEnvironment.programming.blockdisplay.data.{BeBlockDefineVariable, BeBlockUseValue}
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, BeBlockSingleShape, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

trait BeBlockAssignValue extends BeBlock {
  def variable: BeDefineVariable
}


object BeBlockAssignValue {

  def apply(variable: BeDefineVariable, value: BeExpression): BeBlock = BeBlockAssignValueFromExpression(variable, value)

  def apply(variable: BeDefineVariable, value: BeShape): BeBlock = BeBlockAssignValueFromShape(variable, value)

  private case class BeBlockAssignValueFromExpression(variable: BeDefineVariable, value: BeExpression) extends BeBlockSingleShape {

    override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {
      val valueShape: BeShape = childrenShapes.find(_._1.childPosition.roleInParent == ValueInAssignment).get._2
      val assignShape: BeShape = BeBlockAssignValue.BeBlockAssignValueFromShape(variable, valueShape).renderShape(List(), renderingInformation)._2

      val shapeRes = ShapeAroundShape(UnitShape, assignShape)
        .addSignalAmends(renderingInformation.factory.muteOnTreeDragged(renderingInformation.inProgram, renderingInformation.editorState.controllerStateVar.signal, renderingInformation.factory.defaultFunctionColorsAmend))

      (ControlFlowDirected(true, true), shapeRes)
    }

  }

  private case class BeBlockAssignValueFromShape(variable: BeDefineVariable, valueShape: BeShape) extends BeBlockSingleShape {
    override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {

      val dataArrowLeft = BeDataArrow(true)
      val variableRendered: BeShape = BeBlockDefineVariable(variable).renderShape(List(), renderingInformation)._2

      val resShape = HBoxSameHeight(List(variableRendered, dataArrowLeft, valueShape))

      (ControlFlowFunctionCall(), resShape)
    }

  }
}



