package datastructures.core.tree.nodeImpl

import datastructures.core.tree.*
import datastructures.core.tree.nodeImpl.*

case class NodeBasedTreePosition(childIndices: List[Int]) extends TreePosition {

  def forParent(): Option[NodeBasedTreePosition] = if (childIndices.isEmpty) None else Some(NodeBasedTreePosition(childIndices.init))

  def forChild(nr: Int): NodeBasedTreePosition = NodeBasedTreePosition(childIndices ++ List(nr))

  def isRoot: Boolean = childIndices.isEmpty

  val level: Int = childIndices.length

  override def toString: String = if (isRoot) "TreePosition(root)" else childIndices.mkString("TreePosition(root->", "->", ")")


  def relativeTo(newRoot: NodeBasedTreePosition, existingChildrenOfNewRoot: Int): NodeBasedTreePosition =
    if (isRoot) newRoot else {
      val newIndices: List[Int] = newRoot.childIndices ++ List(existingChildrenOfNewRoot + childIndices.head) ++ childIndices.tail
      NodeBasedTreePosition(newIndices)
    }

}

object NodeBasedTreePosition {
  
  val root = NodeBasedTreePosition(List())
  
}
