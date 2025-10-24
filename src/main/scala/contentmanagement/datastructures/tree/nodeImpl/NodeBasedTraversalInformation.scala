package contentmanagement.datastructures.tree.nodeImpl

import contentmanagement.datastructures.tree.*

case class NodeBasedTraversalInformation[D](protected[nodeImpl] val curNode: NodeBasedTreeNode[D], curPosition: NodeBasedTreePosition, private val parentInfo: Option[NodeBasedTraversalInformation[D]], tree: Tree[NodeBasedTreePosition, D])
  extends TreeStructureContext[NodeBasedTreePosition, D]{

  override def traversalInfoForSiblingsInParent: List[NodeBasedTraversalInformation[D]] = traversalInfoForParent.map(_.traversalInfoForChildren).getOrElse(List())
  
  def traversalInfoForParent: Option[NodeBasedTraversalInformation[D]] = parentInfo

  def traversalInfoForChildren: List[NodeBasedTraversalInformation[D]] = {
    curNode.childrenNodes.zipWithIndex.map((curChild, curIndex) => NodeBasedTraversalInformation(curChild, curPosition.forChild(curIndex), Some(this), tree))
  }
  
  val parentValue: Option[D] = parentInfo.map(_.curValue)
  val curValue: D = curNode.data
  val childrenValues: List[D] = curNode.childrenNodes.map(_.data)
  
}