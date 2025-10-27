package interactionPlugins.blockEnvironment.config

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import interactionPlugins.blockEnvironment.programming.{BeBlockTree, BeProgram}


case class BeDraggingEvent(draggedProgram: BeProgram) {
  

}

case class BeMouseOverNode(position: NodeBasedTreePosition, program: BeBlockTree)

case class BeControllerState(programToEdit: BeProgram, draggingEvent: Option[BeDraggingEvent], mouseOverNode: Option[BeMouseOverNode]) {

}

object BeControllerState {

  def defaultForProgram(program: BeProgram): BeControllerState = BeControllerState(program, None, None)

}
