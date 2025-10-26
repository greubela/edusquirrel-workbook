package interactionPlugins.blockEnvironment.config

import contentmanagement.datastructures.tree.TreeStructureContext
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import interactionPlugins.blockEnvironment.programming.{BeBlockTree, BeProgram}


case class BeDraggingEvent(draggedTree: BeProgram)

case class BeMouseOverNode(positionContext: BeBlockTree)


case class BeControllerState(treeToEdit: BeBlockTree, draggingEvent: Option[BeDraggingEvent], mouseOverNode: Option[BeMouseOverNode]) {

}

object BeControllerState {

  def defaultForTree(treeToEdit: BeBlockTree): BeControllerState = BeControllerState(treeToEdit, None, None)

}
