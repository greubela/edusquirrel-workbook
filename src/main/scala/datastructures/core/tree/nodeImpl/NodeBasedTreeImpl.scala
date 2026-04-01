package datastructures.core.tree.nodeImpl

import datastructures.core.tree.*
import datastructures.core.tree.nodeImpl.*

import scala.collection.mutable

class NodeBasedTreeImpl[D](protected val firstLayerNodes: List[NodeBasedTreeNode[D]]) extends Tree[NodeBasedTreePosition, D] {

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

  override def subtreeInclPosition(position: NodeBasedTreePosition): Tree[NodeBasedTreePosition, D] = {
    structureCache.get(position) match {
      case None => NodeBasedTreeImpl[D](List())
      case Some(travelInfo) => NodeBasedTreeImpl(List(travelInfo.curNode))
    }
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


  override def getSubtreeInclLevel(keepInclLevel: Int): Tree[NodeBasedTreePosition, D] = {
    def updateNode(curNode: NodeBasedTraversalInformation[D]): Option[NodeBasedTreeNode[D]] = if (curNode.curPosition.level > keepInclLevel) None else {
      Some(NodeBasedTreeNode[D](curNode.curValue, curNode.traversalInfoForChildren.flatMap(updateNode)))
    }

    NodeBasedTreeImpl[D](firstLayerTravelInfo.flatMap(updateNode))
  }

  override def traverseStructureAndAddChildren(calcChildrenToAdd: TSC => List[(Int, D)], childrenToAddToRoot: List[(Int, D)] = List()): Tree[NodeBasedTreePosition, D] = {

    def insertNodes(oldNodes: List[NodeBasedTreeNode[D]], newNodes: List[(Int, D)]): List[NodeBasedTreeNode[D]] = {
      var transformedChildren = oldNodes
      newNodes.reverse.foreach((index, data) => {
        transformedChildren = oldNodes.slice(0, index) ++ List(NodeBasedTreeNode(data, List())) ++ transformedChildren.slice(index, transformedChildren.size)
      })
      transformedChildren
    }

    def updateNode(curNode: NodeBasedTraversalInformation[D]): NodeBasedTreeNode[D] = {
      val updatedChildren: List[NodeBasedTreeNode[D]] = curNode.traversalInfoForChildren.map(updateNode)
      val childrenToAdd = calcChildrenToAdd(curNode)
      val transformedChildren = insertNodes(updatedChildren, childrenToAdd)
      NodeBasedTreeNode[D](curNode.curValue, transformedChildren)
    }

    val transformedRoots = firstLayerTravelInfo.map(updateNode)
    val transformedRoot = insertNodes(transformedRoots, childrenToAddToRoot)

    NodeBasedTreeImpl(transformedRoot)

  }


  def mapWithStructure[O](transformData: TreeStructureContext[NodeBasedTreePosition, D] => O): NodeBasedTreeImpl[O] = {

    def recreateNode(curNode: TreeStructureContext[NodeBasedTreePosition, D]): NodeBasedTreeNode[O] =
      NodeBasedTreeNode[O](transformData.apply(curNode), curNode.traversalInfoForChildren.map(recreateNode))

    NodeBasedTreeImpl[O](firstLayerTravelInfo.map(curRootNodeTravelInfo => recreateNode(curRootNodeTravelInfo)))
  }

  def applyWithChildResults[O](callFunc: (TreeStructureContext[NodeBasedTreePosition, D], Map[D, O]) => O): Map[NodeBasedTreePosition, O] = {

    val resMap: mutable.Map[NodeBasedTreePosition, O] = mutable.Map[NodeBasedTreePosition, O]()

    def recApply(curNode: NodeBasedTraversalInformation[D]): O = {
      val childResMap = curNode.traversalInfoForChildren.map(curTravInfo => {
        val res = curTravInfo.curValue -> recApply(curTravInfo)
        resMap.put(curTravInfo.curPosition, res._2)
        res
      }).toMap

      callFunc(curNode, childResMap)
    }

    firstLayerTravelInfo.foreach(curRootNodeTravelInfo => {
      val res = recApply(curRootNodeTravelInfo)
      resMap += curRootNodeTravelInfo.curPosition -> res
    })
    resMap.toMap


  }

}

object NodeBasedTreeImpl {

  def empty[D](): NodeBasedTreeImpl[D] = NodeBasedTreeImpl[D](List())
}
