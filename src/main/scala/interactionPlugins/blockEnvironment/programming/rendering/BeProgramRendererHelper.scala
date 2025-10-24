package interactionPlugins.blockEnvironment.programming.rendering

import contentmanagement.datastructures.tree.Tree
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.geometry.{Dimension, Point}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.rendering.*
import scala.collection.mutable

case class BeProgramRendererHelper(config: BeRendererConfig) {

  def toMinSizeTree(tree: Tree[NodeBasedTreePosition, BeBlock]): Tree[NodeBasedTreePosition, Dimension[Double]] = {
    tree.mapWithContext(context => context.curValue.calcMinSize(config, context))
  }

  def toDisplaySizeTree(tree: Tree[NodeBasedTreePosition, BeBlock], minSizeTree: Tree[NodeBasedTreePosition, Dimension[Double]]): Tree[NodeBasedTreePosition, Dimension[Double]] = {
    tree.mapWithContext(context => context.curValue.calcDisplaySize(config, minSizeTree, context))
  }

  def toRelativeOffsetTree(tree: Tree[NodeBasedTreePosition, BeBlock], displaySizeTree: Tree[NodeBasedTreePosition, Dimension[Double]], paddingStart: Dimension[Double]): Tree[NodeBasedTreePosition, Point[Double]] = {
    val offsetMap = mutable.HashMap[NodeBasedTreePosition, Point[Double]]()

    def calcChildrenList(curPosition: NodeBasedTreePosition): List[(BeBlock, NodeBasedTreePosition)] = {
      val children = tree.getChildren(curPosition)
      val res = children.map(curChildPosition => {
        val childBlockAtPos: Option[BeBlock] = tree.getData(curChildPosition)
        (childBlockAtPos.get, curChildPosition)
      })
      res.toList
    }

    def handleAllChildrenOfParent(parentBlock: BeBlockParent, parentPosition: NodeBasedTreePosition): Unit = {
      val allChildren: List[(BeBlock, NodeBasedTreePosition)] = calcChildrenList(parentPosition)

      val finishedChildren = mutable.ListBuffer[(BeBlock, Point[Double], Dimension[Double])]()
      for (curChild <- allChildren) {
        val offset = parentBlock.calcRelativeChildOffsets(config, finishedChildren.toList, curChild._1)
        finishedChildren.append((curChild._1, offset, displaySizeTree.getData(curChild._2).get))
        offsetMap.put(curChild._2, offset)
      }
    }

    def handleRoot(): Unit = {
      val allChildrenFirstLayer = tree.getChildren(tree.rootPosition).map(pos => (tree.getData(pos).get, pos))
      val finishedChildren = mutable.ListBuffer[(BeBlock, Point[Double], Dimension[Double])]()
      for (curChild <- allChildrenFirstLayer) {
        val offset = VBoxParent(false, paddingStart).calcRelativeChildOffsets(config, finishedChildren.toList, curChild._1)
        finishedChildren.append((curChild._1, offset, displaySizeTree.getData(curChild._2).get))
        offsetMap.put(curChild._2, offset)
      }
    }

    def executeForAllParents(): Unit = {
      tree.foreachWithStructure(structure => structure.curValue match {
        case parent: BeBlockParent =>
          handleAllChildrenOfParent(parent, structure.curPosition)
        case _ =>
      })
    }

    executeForAllParents()
    handleRoot()

    tree.mapWithStructure(structure => offsetMap(structure.curPosition))
  }


  def toAbsoluteOffsetTree(tree: Tree[NodeBasedTreePosition, BeBlock], relativeOffsetTree: Tree[NodeBasedTreePosition, Point[Double]]): Tree[NodeBasedTreePosition, Point[Double]] = {
    relativeOffsetTree.mapWithContext(context => if (context.curPosition.level == 1) {
      relativeOffsetTree.getData(context.curPosition).get
    } else {
      context.accessParentResult.get + relativeOffsetTree.getData(context.curPosition).get
    })
  }

  def toBoundsTree(tree: Tree[NodeBasedTreePosition, BeBlock], paddingStart: Dimension[Double]): BeBoundsTree = {
    val minSizeTree = toMinSizeTree(tree)
    val displaySizeTree = toDisplaySizeTree(tree, minSizeTree)
    val relativeOffsetsTree = toRelativeOffsetTree(tree, displaySizeTree, paddingStart)
    val absoluteOffsetsTree = toAbsoluteOffsetTree(tree, relativeOffsetsTree)

    tree.mapWithContext(curContext => {
      val absoluteOffset = absoluteOffsetsTree.getData(curContext.curPosition).get
      val dimension = displaySizeTree.getData(curContext.curPosition).get
      absoluteOffset.withDimension(dimension)
    })
  }

}