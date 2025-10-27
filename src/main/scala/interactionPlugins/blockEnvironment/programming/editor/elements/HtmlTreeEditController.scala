package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeProgram}
import interactionPlugins.blockEnvironment.programming.editor.*
import org.scalajs.dom.MouseEvent
import interactionPlugins.blockEnvironment.config.*

case class HtmlTreeEditController(editorState: TreeEditorState) extends HtmlEditorTreeListener{

  def onClicked(mouseEvent: MouseEvent, eventSource: BeBlockContext): Any = {}

  def onTreeDragged(mouseEvent: MouseEvent, draggedTree: BeProgram): Any = {
    editorState.controllerStateVar.update(oldState => oldState.copy(draggingEvent = Some(BeDraggingEvent(draggedTree))))
    println("Drag started: " + draggedTree)
  }

  def onDragEnded(mouseEvent: MouseEvent): Any = {
    editorState.controllerStateVar.update(oldState => oldState.copy(draggingEvent = None))
    println("Drag ende :)")
  }

  def onDropping(mouseEvent: MouseEvent, eventSource: BeBlockContext): Any = {}

  def onMouseEnter(mouseEvent: MouseEvent, eventSource: BeBlockContext): Any = {}

  def onMouseLeave(mouseEvent: MouseEvent, eventSource: BeBlockContext): Any = {}
  
}
