package interactionPlugins.blockEnvironment.config

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole}
import interactionPlugins.blockEnvironment.programming.{BeBlockTree, BeProgram}


case class BeDraggingEvent(draggedProgram: BeProgram) {


  override val toString: String = "BeDraggingEvent(" + draggedProgram.toString + ")"
}

case class BeMouseOverNode(program: BeProgram, childPosition: BeChildPosition) {

  override val toString: String = "MouseOverNode(" + childPosition + ": " + program.expressionTree.getData(childPosition.parentPosition) + ")"
}

case class BeControllerState(draggingEvent: Option[BeDraggingEvent], mouseOverNode: Option[BeMouseOverNode]) {

}

object BeControllerState {

  def default(): BeControllerState = BeControllerState(None, None)

}
