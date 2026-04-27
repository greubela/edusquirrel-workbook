package interactionPlugins.blockEnvironment.programming.blockdisplay

import contentmanagement.webElements.svg.shapes.special.nested.ShapeWithControlFlow
import contentmanagement.webElements.svg.shapes.{BeShape, BeShapeAmendFactory, ControlFlowAndExpressionShape, ControlFlowShape}
import datastructures.core.vm.code.tree.{BeExpressionNode, BeExtensionPoint}
import interactionPlugins.blockEnvironment.config.{BeRenderingConfig, BeTreeControllerConfig, BeTreeDisplayConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.editor.elements.EditorState
import interactionPlugins.blockEnvironment.rendering.NestedBlockRenderer
import it.evadid.core.datastructures.geometry.Bounds
import it.evadid.core.datastructures.tree.TreeStructureContext
import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition

case class BeTreeDropTarget(extensionPoint: BeExtensionPoint, placeholderForPosition: NodeBasedTreePosition, placeholderBounds: Bounds[Double], placeholderShape: BeShape)

case class RenderingInformation(
                                 inProgram: BeProgram,
                                 displayConfig: BeTreeDisplayConfig,
                                 renderingConfig: BeRenderingConfig,
                                 treeListener: BeTreeControllerConfig,
                                 editorState: EditorState,
                                 registerDropTarget: BeTreeDropTarget => Any
                               ) {
  lazy val factory = BeShapeAmendFactory(renderingConfig)
}


trait BeBlock {

  // todo Methods for toProgramCode (?) Like toPython(): LanguageMap[HumanLanguage] (?) here instead of BeExpression. Block := everything with output AND input (?)

  // todo rename to renderNested and get list of lines instead of the whole renderer Also add other renderer like Struktugramm (?)

  def renderNested(
                    structure: TreeStructureContext[NodeBasedTreePosition, (BeExpressionNode, BeBlock)],
                    childResults: Map[(BeExpressionNode, BeBlock), ControlFlowAndExpressionShape],
                    renderingInfo: RenderingInformation
                  ): ControlFlowAndExpressionShape


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

abstract class BeBlockSingleShape() extends BeBlock {

  override def renderNested(
                             structure: TreeStructureContext[NodeBasedTreePosition, (BeExpressionNode, BeBlock)],
                             childResults: Map[(BeExpressionNode, BeBlock), ControlFlowAndExpressionShape],
                             renderingInfo: RenderingInformation
                           ): ControlFlowAndExpressionShape = {
    
    val childrenShapes: List[(BeExpressionNode, BeShape)] = childResults.toList.map(tup => (tup._1._1, tup._2.expressionShapeOrEverything))
    val (cfShape, exprShape) = renderShape(childrenShapes, renderingInfo)
    ShapeWithControlFlow(cfShape, exprShape)
  }

  override def render(renderedChildren: List[(BeExpressionNode, BeBlock, NestedBlockRenderer)], renderingInfo: RenderingInformation): NestedBlockRenderer = {

    val childrenShapes: List[(BeExpressionNode, BeShape)] = renderedChildren.toList.map(tup => (tup._1, tup._3.expressionShapeWithoutIntendation))
    val (cfShape, exprShape) = renderShape(childrenShapes, renderingInfo)
    NestedBlockRenderer.singleExpressionLineShapeWithInfo(renderedChildren.map(_._3).flatMap(_.allLines), cfShape, exprShape)
  }

  def renderShape(childrenShapes: List[(BeExpressionNode, BeShape)], renderingInformation: RenderingInformation): (ControlFlowShape, BeShape)

}