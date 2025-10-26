package interactionPlugins.blockEnvironment.programming.blocks.parents

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.expressions.{BeExpression, BeSequence}
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockReference.{NewBlock, ReferenceExistingBlock}
import interactionPlugins.blockEnvironment.programming.blocks.traits.*
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockParent, BeBlockReference}
import interactionPlugins.blockEnvironment.programming.shapes.atomic.BeStarterShape
import interactionPlugins.blockEnvironment.programming.shapes.composite.VBoxSameWidth
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}

import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.BodySequence

case class BeBlockStarter(
                         ) extends BeBlockParent with BeBlockStructureUsing {

  override val roleInParent: BeChildRole = BeChildRole.NoRole

  override def getDisplayChildren(displayConfig: BeDisplayConfig, existingChildren: List[ReferenceExistingBlock]): List[BeBlockReference] = {
    existingChildren.filter(_.block.roleInParent.isInstanceOf[BodySequence])
  }

  override protected def render(controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape = {
    val factory = BeShapeAmendFactory(rendererConfig)
    val signalAmends = factory.muteOnTreeDragged(controllerStateVar.signal, factory.defaultStartBlockAmend)

    val starter = BeStarterShape.addSignalAmends(signalAmends)

    VBoxSameWidth(List(starter) ++ renderedDisplayChildren.map(_._2))
  }

  override def changeRole(newRole: BeChildRole): BeBlock = this

  override def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression = {
    BeExpression.NoOp
  }
}
