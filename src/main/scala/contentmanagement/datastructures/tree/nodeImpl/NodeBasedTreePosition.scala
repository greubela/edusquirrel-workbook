package contentmanagement.datastructures.tree.nodeImpl

import contentmanagement.datastructures.tree.*

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
