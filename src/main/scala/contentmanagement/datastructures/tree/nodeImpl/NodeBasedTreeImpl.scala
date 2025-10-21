package contentmanagement.datastructures.tree.nodeImpl


import contentmanagement.datastructures.tree.*
import contentmanagement.datastructures.tree.nodeImpl.*

import scala.collection.mutable

case class NodeBasedTreeImpl[D](private val firstLayerNodes: List[NodeBasedTreeNode[D]]) extends Tree[NodeBasedTreePosition, D] {

  private val firstLayerTravelInfo: List[NodeBasedTraversalInformation[D]] = firstLayerNodes.zipWithIndex.map((curRootChild, curIndex) => NodeBasedTraversalInformation(curRootChild, rootPosition.forChild(curIndex), None, this))

  private lazy val structureCache: Map[NodeBasedTreePosition, NodeBasedTraversalInformation[D]] = {
    val res = mutable.HashMap[NodeBasedTreePosition, NodeBasedTraversalInformation[D]]()

    def recAppend(traversalInfo: NodeBasedTraversalInformation[D]): Unit = {
      traversalInfo.traversalInfoForChildren.foreach(curChildInfo => recAppend(curChildInfo))
      res.put(traversalInfo.curPosition, traversalInfo)
    }

    firstLayerTravelInfo.foreach(curInfo => recAppend(curInfo))
    res.toMap
  }

  override val isEmpty: Boolean = firstLayerNodes.isEmpty

  override def rootPosition: NodeBasedTreePosition = NodeBasedTreePosition(List())

  override def getData(position: NodeBasedTreePosition): Option[D] =
    structureCache.get(position).map(_.curValue)

  override def getParent(position: NodeBasedTreePosition): Option[NodeBasedTreePosition] = if (position.isRoot) None else structureCache.get(position).flatMap(_.traversalInfoForParent.map(_.curPosition))

  override def getChildren(position: NodeBasedTreePosition): List[NodeBasedTreePosition] = if (position.isRoot) firstLayerTravelInfo.map(_.curPosition) else
    structureCache.get(position).map(_.traversalInfoForChildren.map(_.curPosition)).getOrElse(List())


  override def foreachWithStructure(consumer: TreeStructureContext[NodeBasedTreePosition, D] => Any, bottomUp: Boolean = true): Unit = {
    def recTraverse(curNode: NodeBasedTraversalInformation[D]): Unit =
      if (bottomUp) {
        curNode.traversalInfoForChildren.foreach(recTraverse)
        consumer(curNode)
      } else {
        consumer(curNode)
        curNode.traversalInfoForChildren.foreach(recTraverse)
      }

    firstLayerTravelInfo.foreach(recTraverse)
  }


  override def addAsLastChild(positionToAdd: NodeBasedTreePosition, newData: D): Tree[NodeBasedTreePosition, D] = if (positionToAdd.isRoot) {
    NodeBasedTreeImpl[D](firstLayerNodes :+ NodeBasedTreeNode[D](newData, List()))
  } else {
    addAsChildNr(positionToAdd, getChildren(positionToAdd).size, newData)
  }

  override def addSubtreeAsChildNr(insertAtPosition: NodeBasedTreePosition, childNr: Int, subtree: Tree[NodeBasedTreePosition, D]): Tree[NodeBasedTreePosition, D] = {

    var res: Tree[NodeBasedTreePosition, D] = this
    // roots at correct position (in reverse to keep order)
    val subtreeRootPositions = subtree.getChildren(subtree.rootPosition)
    subtreeRootPositions.reverse.foreach(curRootPosition => {
      res = res.addAsChildNr(insertAtPosition, childNr, subtree.getData(curRootPosition).get)
    })
    // remainder step by step at last elements
    subtree.foreachWithStructure(structure =>
      if (structure.curPosition.level > 1) {
        val newPosition = structure.curPosition.relativeTo(insertAtPosition, childNr)
        res = res.addAsLastChild(newPosition.forParent().get, structure.curValue)
      }
      , false)
    res
  }

  private def addNodeAsChildNr(positionToAdd: NodeBasedTreePosition, childNr: Int, newNode: NodeBasedTreeNode[D]): Tree[NodeBasedTreePosition, D] = {
    if (positionToAdd.isRoot) {
      val newRoots = firstLayerNodes.slice(0, childNr) ++ List(newNode) ++ firstLayerNodes.slice(childNr, firstLayerNodes.size)
      NodeBasedTreeImpl(newRoots)
    } else {
      def updateNode(curNode: NodeBasedTraversalInformation[D]): NodeBasedTreeNode[D] = {
        val updatedChildren = curNode.traversalInfoForChildren.map(updateNode)
        val newChildren: List[NodeBasedTreeNode[D]] = if (curNode.curPosition == positionToAdd) {
          updatedChildren.slice(0, childNr) ++ List(newNode) ++ updatedChildren.slice(childNr, updatedChildren.size)
        } else {
          updatedChildren
        }
        NodeBasedTreeNode[D](curNode.curValue, newChildren)
      }

      NodeBasedTreeImpl[D](firstLayerTravelInfo.map(updateNode))
    }
  }

  override def addAsChildNr(positionToAdd: NodeBasedTreePosition, childNr: Int, newData: D): Tree[NodeBasedTreePosition, D] = {
    addNodeAsChildNr(positionToAdd, childNr, NodeBasedTreeNode(newData, List()))
  }

  override def addSubtreeAsLastChild(insertAtPosition: NodeBasedTreePosition, subtree: Tree[NodeBasedTreePosition, D]): Tree[NodeBasedTreePosition, D] = {
    val existingChildren = getChildren(insertAtPosition).size
    addSubtreeAsChildNr(insertAtPosition, existingChildren, subtree)
  }

  override def removePosition(position: NodeBasedTreePosition): Tree[NodeBasedTreePosition, D] = if (position.isRoot) NodeBasedTreeImpl[D](List()) else {
    def updateNode(curNode: NodeBasedTraversalInformation[D]): Option[NodeBasedTreeNode[D]] = if (curNode.curPosition == position) None else {
      Some(NodeBasedTreeNode[D](curNode.curValue, curNode.traversalInfoForChildren.flatMap(updateNode)))
    }

    NodeBasedTreeImpl[D](firstLayerTravelInfo.flatMap(updateNode))

  }


  def mapWithStructure[O](transformData: TreeStructureContext[NodeBasedTreePosition, D] => O): NodeBasedTreeImpl[O] = {

    def recreateNode(curNode: TreeStructureContext[NodeBasedTreePosition, D]): NodeBasedTreeNode[O] =
      NodeBasedTreeNode[O](transformData.apply(curNode), curNode.traversalInfoForChildren.map(recreateNode))

    NodeBasedTreeImpl[O](firstLayerTravelInfo.map(curRootNodeTravelInfo => recreateNode(curRootNodeTravelInfo)))
  }


}

object NodeBasedTreeImpl {

  def empty[D](): NodeBasedTreeImpl[D] = NodeBasedTreeImpl[D](List())
}
