package contentmanagement.datastructures.tree.nodeImpl


import contentmanagement.datastructures.tree.*
import contentmanagement.datastructures.tree.nodeImpl.*
import util.FunctionalUtility

import scala.collection.mutable

case class NodeBasedTreeImpl[D](private val firstLayerNodes: List[NodeBasedTreeNode[D]]) extends Tree[D, NodeBasedTreePosition] {

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

  def foreachInfo(consumer: NodeBasedTraversalInformation[D] => Any, bottomUp: Boolean = true): Unit = {
    def recTraverse(curNode: NodeBasedTraversalInformation[D]): Unit = if (bottomUp) {
      curNode.traversalInfoForChildren.foreach(recTraverse)
      consumer(curNode)
    } else {
      consumer(curNode)
      curNode.traversalInfoForChildren.foreach(recTraverse)
    }

    firstLayerTravelInfo.foreach(recTraverse)
  }

  def mapInfo[O](transformData: TraversalInformation[D, NodeBasedTreePosition] => O): NodeBasedTreeImpl[O] = {

    def recreateNode(curNode: TraversalInformation[D, NodeBasedTreePosition]): NodeBasedTreeNode[O] =
      NodeBasedTreeNode[O](transformData.apply(curNode), curNode.traversalInfoForChildren.map(recreateNode))

    NodeBasedTreeImpl[O](firstLayerTravelInfo.map(curRootNodeTravelInfo => recreateNode(curRootNodeTravelInfo)))
  }


  override def addChild(positionToAdd: NodeBasedTreePosition, newData: D): Tree[D, NodeBasedTreePosition] = if (positionToAdd.isRoot) {
    NodeBasedTreeImpl[D](firstLayerNodes :+ NodeBasedTreeNode[D](newData, List()))
  } else {
    def updateNode(curNode: NodeBasedTraversalInformation[D]): NodeBasedTreeNode[D] = {
      val updatedChildren = curNode.traversalInfoForChildren.map(updateNode)
      val newChildren: List[NodeBasedTreeNode[D]] = if (curNode.curPosition == positionToAdd) updatedChildren :+ NodeBasedTreeNode[D](newData, List()) else updatedChildren

      NodeBasedTreeNode[D](curNode.curValue, newChildren)
    }
    NodeBasedTreeImpl[D](firstLayerTravelInfo.map(updateNode))
  }

  override def removePosition(position: NodeBasedTreePosition): Tree[D, NodeBasedTreePosition] = if (position.isRoot) NodeBasedTreeImpl[D](List()) else {
    def updateNode(curNode: NodeBasedTraversalInformation[D]): Option[NodeBasedTreeNode[D]] = if (curNode.curPosition == position) None else {
      Some(NodeBasedTreeNode[D](curNode.curValue, curNode.traversalInfoForChildren.flatMap(updateNode)))
    }

    NodeBasedTreeImpl[D](firstLayerTravelInfo.flatMap(updateNode))

  }

  override def map[O](function: D => O): Tree[O, NodeBasedTreePosition] = mapInfo(info => function(info.curValue))

  override def foreach(consumer: (NodeBasedTreePosition, D) => Any, bottomUp: Boolean = true): Unit = foreachInfo(info => consumer(info.curPosition, info.curValue), bottomUp)

  override def mapWithContext[O](function: (D, ExecutionContextInfo[NodeBasedTreePosition, D, O]) => O): Tree[O, NodeBasedTreePosition] = {

    def getExecutionContextInfo(curInfo: TraversalInformation[D, NodeBasedTreePosition], curCache: TraversalInformation[D, NodeBasedTreePosition] => O): ExecutionContextInfo[NodeBasedTreePosition, D, O] = new ExecutionContextInfo[NodeBasedTreePosition, D, O]() {

      override def curTraversalInformation: TraversalInformation[D, NodeBasedTreePosition] = curInfo

      override def accessOtherResult(otherPosition: TraversalInformation[D, NodeBasedTreePosition]): O = curCache(otherPosition)
    }

    val func: ((TraversalInformation[D, NodeBasedTreePosition], TraversalInformation[D, NodeBasedTreePosition] => O) => O) = (curInfo, curCache) => function(curInfo.curValue, getExecutionContextInfo(curInfo, curCache))
    val funcCached: (TraversalInformation[D, NodeBasedTreePosition] => O) = FunctionalUtility.withCacheAndResolvedDependencies(func)
    mapInfo(funcCached)
  }


}

object NodeBasedTreeImpl {

  def empty[D](): NodeBasedTreeImpl[D] = NodeBasedTreeImpl[D](List())
}
