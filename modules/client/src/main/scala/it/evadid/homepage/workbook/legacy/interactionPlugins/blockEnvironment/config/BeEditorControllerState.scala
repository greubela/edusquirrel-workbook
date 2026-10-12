package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering.ControlFlowOverlayBuilder.ControlFlowPath
import it.evadid.core.datastructures.geometry.Point
import it.evadid.vm.BeProgram
import it.evadid.vm.code.tree.BeExpressionNode


case class BeDraggingEvent(draggedProgram: BeProgram) derives upickle.default.ReadWriter {

  override val toString: String = "BeDraggingEvent(" + draggedProgram.toString + ")"
}

case class MouseOverProgram(program: BeProgram, position: Point[Double]) derives upickle.default.ReadWriter {

  override val toString: String = "MouseOverProgram(" + position.toString +  "/" + program.toString + ")"

}

case class MouseOverExpression(program: BeProgram, expr: BeExpressionNode) derives upickle.default.ReadWriter

case class TreeDroppedEvent(droppedProgram: BeProgram, position: Point[Double]) derives upickle.default.ReadWriter

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
