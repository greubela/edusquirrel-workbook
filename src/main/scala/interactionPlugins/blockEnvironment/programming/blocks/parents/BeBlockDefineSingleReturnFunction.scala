package interactionPlugins.blockEnvironment.programming.blocks.parents

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.expressions.BeExpression
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockReference.ReferenceExistingBlock
import interactionPlugins.blockEnvironment.programming.blocks.traits.*
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import contentmanagement.model.vm.expressions.defining.*

case class BeBlockDefineSingleReturnFunction(
                                              function: BeDefineFunction,
                                              roleInParent: BeChildRole
                                            )

  extends BeBlockParent with BeBlockStructureDefinition {

  override def getDisplayChildren(displayConfig: BeDisplayConfig, existingChildren: List[ReferenceExistingBlock]): List[BeBlockReference] = ???

  protected def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape = ???

  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(roleInParent = newRole)
  /*
  override def displayShape: BeShape = FunctionDefineShape

  override def parentDisplay: BeParentDisplay = VBoxParent(true, new Dimension[Double](50, 25))


   */

  override def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression = ???

}
