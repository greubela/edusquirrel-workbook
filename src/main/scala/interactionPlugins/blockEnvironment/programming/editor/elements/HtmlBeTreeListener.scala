package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.laminar.api.L.Var
import contentmanagement.datastructures.tree.TreeStructureContext
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import interactionPlugins.blockEnvironment.config.BeControllerState
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeBlockTree, BeProgram}
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.editor.*
import org.scalajs.dom.MouseEvent


trait HtmlBeTreeListener {

  def onClicked(mouseEvent: MouseEvent,
                eventSource: BeBlockContext
               ): Any

  def onTreeDragged(mouseEvent: MouseEvent,
                    draggedTree: BeProgram,
                   ): Any

  def onDragEnded(mouseEvent: MouseEvent,
                 ): Any

  def onDropping(mouseEvent: MouseEvent,
                 eventSource: BeBlockContext
                 ): Any

  def onMouseEnter(mouseEvent: MouseEvent,
                   eventSource: BeBlockContext,
                  ): Any

  def onMouseLeave(mouseEvent: MouseEvent,
                   eventSource: BeBlockContext,
                  ): Any
}

trait HtmlEditorTreeListener extends HtmlBeTreeListener{
  def editorState: TreeEditorState
}

