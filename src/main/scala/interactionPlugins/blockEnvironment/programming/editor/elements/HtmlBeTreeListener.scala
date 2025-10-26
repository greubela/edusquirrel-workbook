package interactionPlugins.blockEnvironment.programming.editor.elements

import contentmanagement.datastructures.tree.TreeStructureContext
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import interactionPlugins.blockEnvironment.programming.BeBlockTree
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import org.scalajs.dom.MouseEvent


trait HtmlBeTreeListener {

  def onClicked(mouseEvent: MouseEvent,
                onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock],
                //  displayConfig: Var[BeTreeDisplayConfig]
               ): Any

  def onTreeDragged(mouseEvent: MouseEvent,
                    draggedTree: BeBlockTree,
                    //    displayConfig: Var[BeTreeDisplayConfig]
                   ): Any

  def onDragEnded(mouseEvent: MouseEvent,
                  draggedTree: BeBlockTree,
                  //   displayConfig: Var[BeTreeDisplayConfig]
                 ): Any

  def onDropping(mouseEvent: MouseEvent,
                 onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock],
                 //    displayConfig: Var[BeTreeDisplayConfig]
                ): Any

  def onMouseEnter(mouseEvent: MouseEvent,
                   onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock],
                   //  displayConfig: Var[BeTreeDisplayConfig]
                  ): Any

  def onMouseLeave(mouseEvent: MouseEvent,
                   onStructure: TreeStructureContext[NodeBasedTreePosition, BeBlock],
                   // displayConfig: Var[BeTreeDisplayConfig]
                  ): Any

}
