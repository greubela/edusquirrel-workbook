package interactionPlugins.blockEnvironment.programming.blocks.parents

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.expressions.defining.BeStartProgram
import contentmanagement.model.vm.expressions.{BeExpression, BeSequence}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.BodySequence
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.blocks.other.*
import interactionPlugins.blockEnvironment.programming.blocks.other.BeBlockReference.*
import interactionPlugins.blockEnvironment.programming.blocks.{BeBlock, BeBlockParent}
import interactionPlugins.blockEnvironment.programming.editor.elements.BeTreeControllerConfig
import interactionPlugins.blockEnvironment.programming.shapes.atomic.BeStarterShape
import interactionPlugins.blockEnvironment.programming.shapes.composite.VBoxSameWidth
import interactionPlugins.blockEnvironment.programming.shapes.{BeShape, BeShapeAmendFactory}

case class BeBlockStarter(
                           override val positionAsChild: BeChildPosition
                         ) extends BeBlockParent {

  override def getDisplayChildren(myPosition: NodeBasedTreePosition, treeControllerConfig: BeTreeControllerConfig, displayConfig: BeDisplayConfig, existingChildren: List[ReferenceExistingBlock]): List[BeBlockReference] = {
    existingChildren.filter(_.block.positionAsChild.roleInParent.isInstanceOf[BodySequence])

  }

  // add the position as id to the shape so that it can be identified via mouseOver? make a droppable container around everything... dirty but should work...
  // or actually search for the nearest possible element to add it to? even simpler :D
  // make a list with possible drop targets in the dragEvent and then check if the mouse is near one of them...
  protected def render(inProgram: BeProgram, controllerStateVar: Var[BeControllerState], rendererConfig: BeRenderingConfig, renderedDisplayChildren: List[(BeBlockReference, BeShape)]): BeShape = {
    val factory = BeShapeAmendFactory(rendererConfig)
    val signalAmends = factory.muteOnTreeDragged(inProgram, controllerStateVar.signal, factory.defaultStartBlockAmend)

    val starter = BeStarterShape.addSignalAmends(signalAmends)

    VBoxSameWidth(List(starter) ++ renderedDisplayChildren.map(_._2), false)
  }

  override def changeRole(newRole: BeChildRole): BeBlock = this.copy(positionAsChild = BeChildPosition(positionAsChild.parentPosition, newRole))

  override def calcAssociatedExpression(childrenWithExpression: List[(BeChildRole, BeExpression)]): BeExpression = {
    val seqChilds = childrenWithExpression.filter(_._1.isInstanceOf[BeChildRole.BodySequence])
    val seq: Seq[BeSequence] = seqChilds.map(_._2).collect { case seq: BeSequence => seq }
    BeStartProgram(seq.headOption.getOrElse(BeSequence(true, List())))
  }
}

