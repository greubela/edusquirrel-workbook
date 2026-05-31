package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.use

import todomove.datastructures.core.vm.code.defining.BeDefineFunction.Operator
import todomove.datastructures.core.vm.types.BeChildRole.FunctionParameter
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.{BeBlockSingleShape, RenderingInformation}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.blockdisplay.data.{BeBlockDefineVariable, BeBlockUseValue}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import todomove.datastructures.core.vm.code.defining.BeDefineVariable
import todomove.datastructures.core.vm.code.tree.BeExpressionNode
import todomove.datastructures.core.vm.code.usage.{BeFunctionCall, BeUseValue}
import todomove.webElementsOld.webElements.svg.shapes.{BeShape, BeShapeAmendFactory, ControlFlowShape, TextShape}
import todomove.webElementsOld.webElements.svg.shapes.composite.{HBoxSameHeight, ShapeAroundShape}
import todomove.webElementsOld.webElements.svg.shapes.controlflow.singleWidth.ControlFlowFunctionCall
import todomove.webElementsOld.webElements.svg.shapes.datatypes.UnitShape
import todomove.webElementsOld.webElements.svg.shapes.decorations.BeDataArrow

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
