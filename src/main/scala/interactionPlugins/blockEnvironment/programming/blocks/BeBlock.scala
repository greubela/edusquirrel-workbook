package interactionPlugins.blockEnvironment.programming.blocks

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.expressions.BeExpression
import contentmanagement.model.vm.types.BeChildRole
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockReference.{NewBlock, ReferenceExistingBlock}
import interactionPlugins.blockEnvironment.programming.shapes.BeShape

sealed trait BeBlock {

  def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig, structure: BeBlockContext): BeShape

  def roleInParent: BeChildRole

  def changeRole(newRole: BeChildRole): BeBlock

  def calcAssociatedExpression(structure: BeBlockContext): BeExpression = {
    val childrenWithExpression: List[(BeChildRole, BeExpression)] = structure
      .traversalInfoForChildren
      .map(curChildTrav => {
        (curChildTrav.curValue.roleInParent, curChildTrav.curValue.calcAssociatedExpression(curChildTrav))
      })
      .filter(curChildExp => curChildExp._2 != BeExpression.NoOp)
    calcAssociatedExpression(childrenWithExpression)
  }

  def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression

}


abstract class BeBlockParent extends BeBlock {

  override def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig, structure: BeBlockContext): BeShape = {
    val childrenRefs: List[ReferenceExistingBlock] = structure.traversalInfoForChildren.zipWithIndex.map((curChildInfo, curChildIndex) => {
      ReferenceExistingBlock(curChildInfo, curChildIndex, curChildInfo.curValue)
    })

    val displayChildren: List[BeBlockReference] = getDisplayChildren(displayConfig, childrenRefs)

    val renderedDisplayChildren: List[(BeBlockReference, BeShape)] = displayChildren.map(curChild => {
      val svgElement = curChild match {
        case ReferenceExistingBlock(childStructure, nrInChildList, block) => block.render(inProgram, controllerStateVar, displayConfig, rendererConfig, childStructure)
        case NewBlock(valueChild) => valueChild.render(inProgram, controllerStateVar, displayConfig, rendererConfig)
        //        protected def render(controllerState: BeControllerState, displayConfig: BeDisplayConfig, config: BeRenderingConfig): AppSvgElement

      }
      (curChild, svgElement)
    })
    val res: BeShape = render(inProgram, controllerStateVar, rendererConfig, renderedDisplayChildren)
    res
  }

  def getDisplayChildren(displayConfig: BeDisplayConfig, existingChildren: List[ReferenceExistingBlock]): List[BeBlockReference]

  protected def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape

}

abstract class BeBlockAtomar extends BeBlock {


  override def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig, structure: BeBlockContext): BeShape = {
    render(inProgram, controllerStateVar, displayConfig, rendererConfig)
  }

  def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], displayConfig: BeDisplayConfig, rendererConfig: BeRenderingConfig): BeShape
  
  def associatedExpression: BeExpression

  override def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression = associatedExpression

}



