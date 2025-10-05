package contentmanagement.datastructures.tree.nodeImpl

import contentmanagement.datastructures.tree.*

case class NodeBasedTreePosition(childIndices: List[Integer]) extends TreePosition {
  def forChild(nr: Integer): NodeBasedTreePosition = NodeBasedTreePosition(childIndices ++ List(nr))

  def isRoot: Boolean = childIndices.isEmpty

  val level: Int = childIndices.length

  override def toString: String = if(isRoot) "TreePosition(root)" else childIndices.mkString("TreePosition(root->", "->", ")")

}
