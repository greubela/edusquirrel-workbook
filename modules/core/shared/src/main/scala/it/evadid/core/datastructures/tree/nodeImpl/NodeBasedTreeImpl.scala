package it.evadid.core.datastructures.tree.nodeImpl

import it.evadid.core.datastructures.tree.{Tree, TreeStructureContext}


class NodeBasedTreeImpl[D](protected val firstLayerNodes: List[NodeBasedTreeNode[D]]) extends Tree[NodeBasedTreePosition, D] {

  private val firstLayerTravelInfo: List[NodeBasedTraversalInformation[D]] = firstLayerNodes.zipWithIndex.map((curRootChild, curIndex) => NodeBasedTraversalInformation(curRootChild, rootPosition.forChild(curIndex), None, this))

  private def traversal(bottomUp: Boolean): Iterator[NodeBasedTraversalInformation[D]] = {
    def visit(info: NodeBasedTraversalInformation[D]): Iterator[NodeBasedTraversalInformation[D]] =
      if bottomUp then info.traversalInfoForChildren.iterator.flatMap(visit) ++ Iterator.single(info)
      else Iterator.single(info) ++ info.traversalInfoForChildren.iterator.flatMap(visit)
    firstLayerTravelInfo.iterator.flatMap(visit)
  }

  private lazy val structureCache = traversal(false).map(info => info.curPosition -> info).toMap

  override val isEmpty: Boolean = firstLayerNodes.isEmpty

  override def rootPosition: NodeBasedTreePosition = NodeBasedTreePosition(List())

  override def getData(position: NodeBasedTreePosition): Option[D] =
    structureCache.get(position).map(_.curValue)

  override def getParent(position: NodeBasedTreePosition): Option[NodeBasedTreePosition] = if (position.isRoot) None else structureCache.get(position).flatMap(_.traversalInfoForParent.map(_.curPosition))

  override def getChildren(position: NodeBasedTreePosition): List[NodeBasedTreePosition] = if (position.isRoot) firstLayerTravelInfo.map(_.curPosition) else
    structureCache.get(position).map(_.traversalInfoForChildren.map(_.curPosition)).getOrElse(List())


  override def foreachWithStructure(consumer: TreeStructureContext[NodeBasedTreePosition, D] => Any, bottomUp: Boolean = true): Unit =
    traversal(bottomUp).foreach(consumer)

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
    def copyNode(position: NodeBasedTreePosition): NodeBasedTreeNode[D] =
      NodeBasedTreeNode(subtree.getData(position).get, subtree.getChildren(position).map(copyNode))
    insertChildren(insertAtPosition, childNr, subtree.getChildren(subtree.rootPosition).map(copyNode))
  }

  private def insertChildren(position: NodeBasedTreePosition, index: Int, nodes: List[NodeBasedTreeNode[D]]): NodeBasedTreeImpl[D] = {
    def updateNode(info: NodeBasedTraversalInformation[D]): NodeBasedTreeNode[D] = {
      val children = info.traversalInfoForChildren.map(updateNode)
      NodeBasedTreeNode(info.curValue, if info.curPosition == position then children.patch(index, nodes, 0) else children)
    }
    NodeBasedTreeImpl(if position.isRoot then firstLayerNodes.patch(index, nodes, 0) else firstLayerTravelInfo.map(updateNode))
  }

  override def addAsChildNr(positionToAdd: NodeBasedTreePosition, childNr: Int, newData: D): Tree[NodeBasedTreePosition, D] = {
    insertChildren(positionToAdd, childNr, List(NodeBasedTreeNode(newData, Nil)))
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
      newNodes.sortBy(_._1).foldRight(oldNodes) { case ((index, data), children) =>
        children.patch(index, List(NodeBasedTreeNode(data, Nil)), 0)
      }
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

  def applyWithChildResults[O](callFunc: (TreeStructureContext[NodeBasedTreePosition, D], Map[D, O]) => O): Map[NodeBasedTreePosition, O] =
    traversal(true).foldLeft(Map.empty[NodeBasedTreePosition, O]) { (results, info) =>
      val children = info.traversalInfoForChildren.map(child => child.curValue -> results(child.curPosition)).toMap
      results.updated(info.curPosition, callFunc(info, children))
    }

}

object NodeBasedTreeImpl {

  def empty[D](): NodeBasedTreeImpl[D] = NodeBasedTreeImpl[D](List())
}
