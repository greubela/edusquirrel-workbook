package interactionPlugins.blockEnvironment.programming.blockdisplay.use

import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.defining.BeDefineFunction.Operator
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.usage.{BeFunctionCall, BeUseValue}
import contentmanagement.model.vm.types.BeChildRole.FunctionParameter
import contentmanagement.webElements.svg.shapes.composite.{HBoxSameHeight, ShapeAroundShape}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.datatypes.UnitShape
import contentmanagement.webElements.svg.shapes.decorations.BeDataArrow
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeAmendFactory, ControlFlowShape, TextShape}
import interactionPlugins.blockEnvironment.programming.blockdisplay.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.data.{BeBlockDefineVariable, BeBlockUseValue}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockCallSingleReturnFunction(
                                            function: BeFunctionCall,
                                          ) extends BeBlockSingleShape {


  override def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape) = {

    val parameterChildrenInOrder: List[BeShape] = childrenShapes
      .filter(_._1.childPosition.roleInParent.isInstanceOf[FunctionParameter])
      .sortBy(_._1.childPosition.roleInParent.asInstanceOf[FunctionParameter].nr)
      .map(_._2)

    val nameShape = TextShape(function.funcDef.functionTypeInfo.displayName)
    val namePos = function.funcDef.functionTypeInfo.displayNamePosition

    val childrenWithName: List[BeShape] = parameterChildrenInOrder.slice(0, namePos) ++ List(nameShape) ++ parameterChildrenInOrder.slice(namePos, parameterChildrenInOrder.size)
    val childBox = HBoxSameHeight(childrenWithName, true)

    val factory = BeShapeAmendFactory(renderingInformation.renderingConfig)
    val signalAmends = factory.muteOnTreeDragged(renderingInformation.inProgram, renderingInformation.editorState.controllerStateVar.signal, factory.defaultFunctionColorsAmend)

    val outputShape = function.funcDef.outputs.map(_.variableType.createContainerShape.get).getOrElse(UnitShape)

    val shape = ShapeAroundShape(outputShape, childBox).addSignalAmends(signalAmends)

    (ControlFlowFunctionCall(), shape)

  }


}
