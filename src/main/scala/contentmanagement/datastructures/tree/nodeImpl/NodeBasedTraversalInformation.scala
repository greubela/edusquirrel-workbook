package contentmanagement.datastructures.tree.nodeImpl

import contentmanagement.datastructures.tree.*


case class NodeBasedTraversalInformation[D](private val curNode: NodeBasedTreeNode[D], curPosition: NodeBasedTreePosition, private val parentInfo: Option[NodeBasedTraversalInformation[D]], tree: Tree[D, NodeBasedTreePosition])
  extends TraversalInformation[D, NodeBasedTreePosition]{

  
  def traversalInfoForParent: Option[NodeBasedTraversalInformation[D]] = parentInfo

  def traversalInfoForChildren: List[NodeBasedTraversalInformation[D]] = {
    curNode.childrenNodes.zipWithIndex.map((curChild, curIndex) => NodeBasedTraversalInformation(curChild, curPosition.forChild(curIndex), Some(this), tree))

  }
  


  val parentValue: Option[D] = parentInfo.map(_.curValue)
  val curValue: D = curNode.data
  val childrenValues: List[D] = curNode.childrenNodes.map(_.data)

  
}