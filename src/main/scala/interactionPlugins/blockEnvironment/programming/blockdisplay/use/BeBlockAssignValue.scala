package interactionPlugins.blockEnvironment.programming.blockdisplay.use

import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.usage.BeUseValue
import contentmanagement.model.vm.types.BeChildRole.ValueInAssignment
import contentmanagement.webElements.svg.shapes.BeShape
import contentmanagement.webElements.svg.shapes.composite.{HBoxSameHeight, ShapeAroundShape}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.{ControlFlowDirected, ControlFlowFunctionCall}
import contentmanagement.webElements.svg.shapes.datatypes.UnitShape
import contentmanagement.webElements.svg.shapes.decorations.BeDataArrow
import interactionPlugins.blockEnvironment.programming.blockdisplay.data.{BeBlockDefineVariable, BeBlockUseValue}
import interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlock, RenderingInformation}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

trait BeBlockAssignValue extends BeBlock {
  def variable: BeDefineVariable
}


object BeBlockAssignValue {

  def apply(variable: BeDefineVariable, value: BeExpression): BeBlock = BeBlockAssignValueFromExpression(variable, value)

  def apply(variable: BeDefineVariable, value: BeShape): BeBlock = BeBlockAssignValueFromShape(variable, value)

  private case class BeBlockAssignValueFromExpression(variable: BeDefineVariable, value: BeExpression) extends BeBlock {

    override def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {
      val valueShape: BeShape = renderedChildren.find(_._1.childPosition.roleInParent == ValueInAssignment).get._3.firstExpressionShapeOrHBox()
      val assignShape = BeBlockAssignValue.BeBlockAssignValueFromShape(variable, valueShape).render(renderedChildren, renderingInfo)

      val shapeRes = ShapeAroundShape(UnitShape, assignShape.firstExpressionShapeOrHBox())
        .addSignalAmends(renderingInfo.factory.muteOnTreeDragged(renderingInfo.inProgram, renderingInfo.editorState.controllerStateVar.signal, renderingInfo.factory.defaultFunctionColorsAmend))

      NestedBlockRenderer.singleExpressionLineShapeWithInfo(List(), ControlFlowDirected(true, true), shapeRes)
    }

  }

  private case class BeBlockAssignValueFromShape(variable: BeDefineVariable, valueShape: BeShape) extends BeBlock {
    override def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

      val dataArrowLeft = BeDataArrow(true)
      val variableRendered: BeShape = BeBlockDefineVariable(variable).render(renderedChildren, renderingInfo).firstExpressionShapeOrHBox(true)

      val resShape = HBoxSameHeight(List(variableRendered, dataArrowLeft, valueShape))

      NestedBlockRenderer.singleExpressionLineShapeWithInfo(List(), ControlFlowFunctionCall(), resShape)
    }

  }
}



