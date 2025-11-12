package interactionPlugins.blockEnvironment.programming.blockdisplay.use

import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.defining.BeDefineFunction.Operator
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.usage.{BeFunctionCall, BeUseValue}
import contentmanagement.model.vm.types.BeChildRole.FunctionParameter
import contentmanagement.webElements.svg.shapes.composite.{HBoxSameHeight, ShapeAroundShape}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.datatypes.UnitShape
import contentmanagement.webElements.svg.shapes.decorations.BeDataArrow
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeAmendFactory, TextShape}
import interactionPlugins.blockEnvironment.programming.blockdisplay.*
import interactionPlugins.blockEnvironment.programming.blockdisplay.data.{BeBlockDefineVariable, BeBlockUseValue}
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockCallSingleReturnFunction(
                                            function: BeFunctionCall,
                                          ) extends BeBlock {


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val allLines: List[NestedBlockRenderer.NestedBlockLine] = renderedChildren.map(_._3).flatMap(_.allLines)

    def transformValueShape(nbr: NestedBlockRenderer, shape: BeShape, parameter: FunctionParameter): BeShape = {
      if (function.funcDef.functionTypeInfo.funcType == Operator || renderingInfo.displayConfig.compactFunctionCalls) {
        shape
      } else {
        val variableShape = BeBlockDefineVariable(function.funcDef.inputs(parameter.nr)).render(renderedChildren, renderingInfo).firstExpressionShapeOrHBox()
        val dataArrowLeft = BeDataArrow(true)

        HBoxSameHeight(List(variableShape, dataArrowLeft, shape), true)
      }
    }

    val parameterChildrenInOrder: List[BeShape] = renderedChildren
      .filter(_._1.childPosition.roleInParent.isInstanceOf[FunctionParameter])
      .map(tup => (tup._3, tup._3.firstExpressionShapeOrHBox(), tup._1.childPosition.roleInParent.asInstanceOf[FunctionParameter]))
      .sortBy(_._3.nr)
      .map(tup => transformValueShape(tup._1, tup._2, tup._3))

    val nameShape = TextShape(function.funcDef.functionTypeInfo.displayName)
    val namePos = function.funcDef.functionTypeInfo.displayNamePosition

    val childrenWithName: List[BeShape] = parameterChildrenInOrder.slice(0, namePos) ++ List(nameShape) ++ parameterChildrenInOrder.slice(namePos, parameterChildrenInOrder.size)
    val childBox = HBoxSameHeight(childrenWithName, true)

    val factory = BeShapeAmendFactory(renderingInfo.renderingConfig)
    val signalAmends = factory.muteOnTreeDragged(renderingInfo.inProgram, renderingInfo.editorState.controllerStateVar.signal, factory.defaultFunctionColorsAmend)

    val outputShape = function.funcDef.outputs.map(_.variableType.createContainerShape.get).getOrElse(UnitShape)

    val shape = ShapeAroundShape(outputShape, childBox).addSignalAmends(signalAmends)

    NestedBlockRenderer.singleExpressionLineShapeWithInfo(allLines, ControlFlowFunctionCall(), shape)

  }


}
