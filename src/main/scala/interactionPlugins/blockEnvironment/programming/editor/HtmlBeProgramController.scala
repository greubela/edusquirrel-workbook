package interactionPlugins.blockEnvironment.programming.editor

import contentmanagement.datastructures.tree.Tree
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.traits.BeBlockLogic
import org.scalajs.dom.MouseEvent

trait HtmlBeProgramController {

  
  
  def notifyOnMouseEnter(mouseEvent: MouseEvent, appSvgElement: AppSvgElement, block: BeBlock): Unit

  // todo all others

  def canAcceptDrop(onPosition: NodeBasedTreePosition, draggedTree: Tree[NodeBasedTreePosition, BeBlockLogic]): Boolean

  def insertBelow(onPosition: NodeBasedTreePosition, treeToInsert: Tree[NodeBasedTreePosition, BeBlockLogic]): Unit

}


trait BeProgramMouseListener {

  def onDraggedStart(mouseEvent: MouseEvent, svgElement: AppSvgElement): Unit

  def onElementDropped(mouseEvent: MouseEvent, svgElement: AppSvgElement): Unit

  def onElementClicked(mouseEvent: MouseEvent, svgElement: AppSvgElement): Unit

  def onEnteredElement(mouseEvent: MouseEvent, svgElement: AppSvgElement): Unit

  def onElementExited(mouseEvent: MouseEvent, svgElement: AppSvgElement): Unit

}