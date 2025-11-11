package interactionPlugins.blockEnvironment.programming.blockdisplay

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.datastructures.tree.TreeStructureContext
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.webElements.svg.shapes.BeShapeAmendFactory
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeRenderingConfig, BeTreeDisplayConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.editor.elements.BeTreeControllerConfig
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer

case class RenderingInformation(inProgram: BeProgram, displayConfig: BeTreeDisplayConfig, renderingConfig: BeRenderingConfig, treeListener: BeTreeControllerConfig, controllerStateVar: Var[BeControllerState]) {
  lazy val factory = BeShapeAmendFactory(renderingConfig)
}


trait BeBlock {

  // todo Methods for toProgramCode (?) Like toPython(): LanguageMap[HumanLanguage] (?) here instead of BeExpression. Block := everything with output AND input (?)

  // todo rename to renderNested and get list of lines instead of the whole renderer Also add other renderer like Struktugramm (?)

  def renderOld(structure: BeBlockRenderingContext, renderingInfo: RenderingInformation): NestedBlockRenderer = {
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


  def render(structure: TreeStructureContext[NodeBasedTreePosition, (BeExpressionNode, BeBlock)], childResults: Map[(BeExpressionNode, BeBlock), NestedBlockRenderer], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)] = childResults.keys.map(tup => (tup._1, tup._2, childResults(tup))).toList

    /*val 

    }*/

    render(renderedChildren, renderingInfo)
  }

  def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer

}
