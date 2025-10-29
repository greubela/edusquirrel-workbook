package interactionPlugins.blockEnvironment.programming.blocks

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{Signal, Var}
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeScope}
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.other.BeBlockReference
import interactionPlugins.blockEnvironment.programming.blocks.other.BeBlockReference.{NewBlock, ReferenceExistingBlock}
import interactionPlugins.blockEnvironment.programming.editor.elements.BeTreeControllerConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import sourcecode.Text.generate

abstract class BeBlock {

  def render(inProgram: BeProgram, listener: BeTreeControllerConfig, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig, structure: BeBlockContext): BeShape

  def positionAsChild: BeChildPosition

  def changeRole(newRole: BeChildRole): BeBlock 

  def calcAssociatedExpression(structure: BeBlockContext): BeExpression = {
    val childrenWithExpression: List[(BeChildRole, BeExpression)] = structure
      .traversalInfoForChildren
      .map(curChildTrav => {
        (curChildTrav.curValue.positionAsChild.roleInParent, curChildTrav.curValue.calcAssociatedExpression(curChildTrav))
      })
      .filter(curChildExp => curChildExp._2 != BeExpression.NoOp)
    calcAssociatedExpression(childrenWithExpression)
  }

  def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression

  
}


abstract class BeBlockParent extends BeBlock {

  override def render(inProgram: BeProgram, treeControllerConfig: BeTreeControllerConfig, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig, structure: BeBlockContext): BeShape = {
    val childrenRefs: List[ReferenceExistingBlock] = structure.traversalInfoForChildren.zipWithIndex.map((curChildInfo, curChildIndex) => {
      ReferenceExistingBlock(curChildInfo, curChildIndex, curChildInfo.curValue)
    })

    val displayChildren: List[BeBlockReference] = getDisplayChildren(structure.curPosition, treeControllerConfig, displayConfig, childrenRefs)

    val renderedDisplayChildren: List[(BeBlockReference, BeShape)] = displayChildren.map(curChild => {
      val svgElement = curChild match {
        case ReferenceExistingBlock(childStructure, nrInChildList, block) => block.render(inProgram, treeControllerConfig, controllerStateVar, displayConfig, rendererConfig, childStructure)
        case NewBlock(valueChild) => valueChild.render(inProgram, treeControllerConfig, controllerStateVar, displayConfig, rendererConfig)
        //        protected def render(controllerState: BeControllerState, displayConfig: BeDisplayConfig, config: BeRenderingConfig): AppSvgElement

      }
      (curChild, svgElement)
    })
    render(inProgram, controllerStateVar, rendererConfig, renderedDisplayChildren)
      .addAmends(treeControllerConfig.getMouseAmendsForPosition(inProgram, positionAsChild))
    //  .addAmends(treeControllerConfig.getDragDropAmends(inProgram))

  }

  def getDisplayChildren(myPosition: NodeBasedTreePosition, treeControllerConfig: BeTreeControllerConfig, displayConfig: BeDisplayConfig, existingChildren: List[ReferenceExistingBlock]): List[BeBlockReference]

  protected def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape

}

abstract class BeBlockAtomar extends BeBlock {
  
  override def render(inProgram: BeProgram, listener: BeTreeControllerConfig, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig, structure: BeBlockContext): BeShape = {
    render(inProgram, controllerStateVar, displayConfig, rendererConfig).addAmends(listener.getMouseAmendsForPosition(inProgram, positionAsChild))
  }

  def render(inProgram: BeProgram, listener: BeTreeControllerConfig, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape
  = render(inProgram, controllerStateVar, displayConfig, rendererConfig).addAmends(listener.getMouseAmendsForPosition(inProgram, positionAsChild))

  def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape

  def associatedExpression: BeExpression

  override def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression = associatedExpression

}



