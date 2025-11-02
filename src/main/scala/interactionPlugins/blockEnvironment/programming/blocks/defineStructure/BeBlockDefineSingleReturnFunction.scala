package interactionPlugins.blockEnvironment.programming.blocks.defineStructure

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.editor.elements.BeTreeControllerConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape

case class BeBlockDefineSingleReturnFunction(
                                            beDefineFunction: BeDefineFunction
                                            )  extends BeBlock  {


  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = ???
}
