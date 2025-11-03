package interactionPlugins.blockEnvironment.config

import contentmanagement.model.vm.code.tree.BeExtensionPoint
import interactionPlugins.blockEnvironment.programming.BeProgram


case class BeDraggingEvent(draggedProgram: BeProgram) {


  override val toString: String = "BeDraggingEvent(" + draggedProgram.toString + ")"
}

case class BeMouseOverExtensionPoint(program: BeProgram, extensionPoint: BeExtensionPoint) {
  override val toString: String = "MouseOverNode(" + extensionPoint + ")"
}

case class BeControllerState(draggingEvent: Option[BeDraggingEvent], mouseOverNode: Option[BeMouseOverExtensionPoint]) {

}

object BeControllerState {

  def default(): BeControllerState = BeControllerState(None, None)

}
