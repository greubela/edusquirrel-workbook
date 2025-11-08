package interactionPlugins.blockEnvironment.programming.blockdisplay.use

import com.raquo.laminar.api.L
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.usage.BeFunctionCall
import contentmanagement.model.vm.types.*
import contentmanagement.webElements.svg.shapes.composite.{HBoxSameHeight, ShapeAroundShape}
import contentmanagement.webElements.svg.shapes.controlflow.singleWidth.*
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeAmendFactory, TextShape}
import interactionPlugins.blockEnvironment.programming.blockdisplay.*
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class BeBlockCallSingleReturnFunction(
                                            function: BeFunctionCall,
                                          ) extends BeBlock {


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val allLines: List[NestedBlockRenderer.NestedBlockLine] = renderedChildren.map(_._3).flatMap(_.allLines)

    val nameShape = TextShape(function.funcDef.functionTypeInfo.displayName)
    val namePos = function.funcDef.functionTypeInfo.displayNamePosition

    val childrenShapes = allLines.flatMap(_.expressionShape)
    val childrenWithName: List[BeShape] = childrenShapes.slice(0, namePos) ++ List(nameShape) ++ childrenShapes.slice(namePos, childrenShapes.size)
    val childBox = HBoxSameHeight(childrenWithName )

    val factory = BeShapeAmendFactory(renderingInfo.renderingConfig)
    val signalAmends = factory.muteOnTreeDragged(renderingInfo.inProgram, renderingInfo.controllerStateVar.signal, factory.defaultFunctionColorsAmend)

    val shape = ShapeAroundShape(function.canEvaluateTo.createContainerShape.get, childBox).addSignalAmends(signalAmends)

    NestedBlockRenderer.singleExpressionLineShapeWithInfo(allLines, ControlFlowFunctionCall(), shape)

  }


}
