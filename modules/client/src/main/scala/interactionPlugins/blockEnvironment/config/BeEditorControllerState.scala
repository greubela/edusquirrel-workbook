package interactionPlugins.blockEnvironment.config

import datastructures.core.vm.code.tree.BeExpressionNode
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.ControlFlowPath
import it.evadid.core.datastructures.geometry.Point


case class BeDraggingEvent(draggedProgram: BeProgram) {

  override val toString: String = "BeDraggingEvent(" + draggedProgram.toString + ")"
}

case class MouseOverProgram(program: BeProgram, position: Point[Double]) {

  override val toString: String = "MouseOverProgram(" + position.toString +  "/" + program.toString + ")"

}

case class MouseOverExpression(program: BeProgram, expr: BeExpressionNode)

case class TreeDroppedEvent(droppedProgram: BeProgram, position: Point[Double])

case class BeEditorControllerState(
                                    draggingEvent: Option[BeDraggingEvent],
                                    mouseOverExpression: Option[MouseOverExpression],
                                    mouseDragOverProgram: Option[MouseOverProgram],
                                    mouseOverControlFlow: Option[ControlFlowPath],
                                    unhandledDropEvents: Option[TreeDroppedEvent]) {

  override val toString: String =
    "BeEditorControllerState("
      + draggingEvent.nonEmpty + ", "
      + mouseOverExpression.nonEmpty + ", "
      + mouseDragOverProgram.map(_.position) + ", "
      + mouseOverControlFlow.map(_.curStatus) + ", "
      + unhandledDropEvents.map(_.position) + ")"

}

object BeEditorControllerState {

  def default(): BeEditorControllerState = BeEditorControllerState(None, None, None, None, None)

}
