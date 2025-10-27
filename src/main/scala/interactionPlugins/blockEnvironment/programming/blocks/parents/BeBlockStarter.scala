package interactionPlugins.blockEnvironment.programming.blocks.parents

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.model.vm.expressions.defining.BeStartProgram
import contentmanagement.model.vm.expressions.{BeExpression, BeSequence}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.BodySequence
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.BeBlockReference.ReferenceExistingBlock
import interactionPlugins.blockEnvironment.programming.blocks.traits.*
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockParent, BeBlockReference}
import interactionPlugins.blockEnvironment.programming.shapes.atomic.BeStarterShape
import interactionPlugins.blockEnvironment.programming.shapes.composite.VBoxSameWidth
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}

case class BeBlockStarter(
                         ) extends BeBlockParent with BeBlockStructureUsing {

  override val roleInParent: BeChildRole = BeChildRole.NoRole

  override def getDisplayChildren(displayConfig: BeDisplayConfig, existingChildren: List[ReferenceExistingBlock]): List[BeBlockReference] = {
    existingChildren.filter(_.block.roleInParent.isInstanceOf[BodySequence])
  }

  protected def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape = {
    val factory = BeShapeAmendFactory(rendererConfig)
    val signalAmends = factory.muteOnTreeDragged(controllerStateVar.signal, factory.defaultStartBlockAmend)

    val starter = BeStarterShape.addSignalAmends(signalAmends)

    VBoxSameWidth(List(starter) ++ renderedDisplayChildren.map(_._2))
  }

  override def changeRole(newRole: BeChildRole): BeBlock = this

  override def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression = {
    val seqChilds = childrenWithExpression.filter(_._1.isInstanceOf[BeChildRole.BodySequence])
    val seq: Seq[BeSequence] = seqChilds.map(_._2).collect{case seq: BeSequence => seq}
    BeStartProgram(seq.headOption.getOrElse(BeSequence(List(), true, Some(Set(BeDataType.Unit)))))
  }
}

