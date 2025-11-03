package interactionPlugins.blockEnvironment.programming.blocks.using

import contentmanagement.model.vm.code.defining.*
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.usage.BeFunctionCall
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.{FunctionParameter, NoRole}
import contentmanagement.webElements.svg.shapes.{BeShapeAmendFactory, TextShape}
import contentmanagement.webElements.svg.shapes.composite.{HBoxSameHeight, ShapeAroundShape}
import interactionPlugins.blockEnvironment.config.*
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.variable.*
import interactionPlugins.blockEnvironment.programming.editor.elements.BeTreeControllerConfig

import scala.collection.mutable

case class BeBlockCallSingleReturnFunction(
                                            function: BeFunctionCall,
                                          ) extends BeBlock {


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val allLines = renderedChildren.flatMap(_._3.lines)

    val nameShape = TextShape(function.funcDef.functionTypeInfo.displayName)
    val childrenShapes = allLines.map(_.mainShape)


    val childBox = HBoxSameHeight(List(nameShape) ++ childrenShapes)
    val factory = BeShapeAmendFactory(renderingInfo.renderingConfig)

    val signalAmends = factory.muteOnTreeDragged(renderingInfo.inProgram, renderingInfo.controllerStateVar.signal, factory.defaultFunctionColorsAmend)

    val shape = ShapeAroundShape(function.canEvaluateTo.createContainerShape.get, childBox)
      .addSignalAmends(signalAmends)

    NestedBlockRenderer.fromLinesWithNewShape(allLines, shape)

  }


}
