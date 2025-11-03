package interactionPlugins.blockEnvironment.programming.blocks

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.BeDataType
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeAmendFactory}
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.editor.elements.BeTreeControllerConfig

case class RenderingInformation(inProgram: BeProgram, displayConfig: BeDisplayConfig, renderingConfig: BeRenderingConfig, treeListener: BeTreeControllerConfig, controllerStateVar: Var[BeControllerState]) {
  lazy val factory = BeShapeAmendFactory(renderingConfig)
}


trait BeBlock {

  def render(structure: BeBlockRenderingContext, renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)] = {
      structure
        .traversalInfoForChildren
        .zip(structure.accessChildrenResults)
        .map((curStructure, childRes) => (
          curStructure.curValue._1,
          curStructure.curValue._2,
          childRes
        )
        )
    }

    render(renderedChildren, renderingInfo)
  }

  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer

}
