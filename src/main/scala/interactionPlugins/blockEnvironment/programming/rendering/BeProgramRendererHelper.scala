package interactionPlugins.blockEnvironment.programming.rendering

import contentmanagement.datastructures.tree.Tree
import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import contentmanagement.model.geometry.{Dimension, Point}
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.display.*
import interactionPlugins.blockEnvironment.programming.blocks.logic.parent.BeBlockParent

import scala.collection.mutable

case class BeProgramRendererHelper(config: BeRendererConfig) {


  def toMinSizeTree(tree: Tree[NodeBasedTreePosition, BeBlock]): Tree[NodeBasedTreePosition, Dimension[Double]] = {
    tree.mapWithContext(context => context.curValue.layoutManager.calcMinSize(config, context))
  }

  def toDisplaySizeTree(tree: Tree[NodeBasedTreePosition, BeBlock], minSizeTree: Tree[NodeBasedTreePosition, Dimension[Double]]): Tree[NodeBasedTreePosition, Dimension[Double]] = {
    tree.mapWithContext(context => context.curValue.layoutManager.calcDisplaySize(config, minSizeTree, context))
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
      val children: List[(BeBlock, NodeBasedTreePosition)] = calcChildrenList(parentPosition)
      handleWithManager(parentBlock.parentDisplayManager, children)
    }

    def handleWithManager(parentDisplayManager: BeBlockParentDisplayManager, children: List[(BeBlock, NodeBasedTreePosition)]): Unit = {
      val finishedChildren = mutable.ListBuffer[(BeBlock, Point[Double], Dimension[Double])]()
      for (curChild <- children) {
        val offset = parentDisplayManager.calcRelativeChildOffsets(config, finishedChildren.toList, curChild._1)
        finishedChildren.append((curChild._1, offset, displaySizeTree.getData(curChild._2).get))
        offsetMap.put(curChild._2, offset)
      }
    }

    def handleRoot(): Unit = {
      val rootLayoutManager = new BeBlockParentDisplayManager() {
        def calcRelativeChildOffsets(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double] =
          BeBlockParentDisplayManager.calculateRelativeOffsetsAsVBox(config, childrenBefore, curChild, paddingStart, false)
      }
      val firstLayer = tree.getChildren(tree.rootPosition).map(pos => (tree.getData(pos).get, pos))
      handleWithManager(rootLayoutManager, firstLayer)
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

    println("minSizeTree: " + minSizeTree)

    tree.mapWithContext(curContext => {
      val absoluteOffset = absoluteOffsetsTree.getData(curContext.curPosition).get
      val dimension = displaySizeTree.getData(curContext.curPosition).get
      absoluteOffset.withDimension(dimension)
    })
  }


}

object BeProgramRendererHelper {


  def calcDisplayTree(logicTree: BeProgramLogicTree): BeProgramDisplayTree = {

    def calcToAddToTreeForPos(pos: NodeBasedTreePosition, curParent: BeBlockParent, existingChildren: List[BeBlock]): List[BeBlockDisplay] = {
      val addToCurBlock = mutable.ListBuffer[BeBlockDisplay]()
      curParent.getConnections.foreach(curConnection => {
        val nrOfTypeAlreadyExisting = existingChildren.count(_.roleInParent == curConnection.connectionRole)
        val maximumReached = curConnection.connectionCardinality.hasMaximum && nrOfTypeAlreadyExisting >= curConnection.connectionCardinality.maximumIncl.get
        if (!maximumReached) {
          addToCurBlock.append(BeBlockDummyMultipleTypes(curConnection.connectionMayEvaluateTo, curConnection.connectionRole))
        }
      })
      addToCurBlock.toList
    }

    def calcToAddToTree(): List[(NodeBasedTreePosition, List[BeBlockDisplay])] = {
      val addToTree = mutable.ListBuffer[(NodeBasedTreePosition, List[BeBlockDisplay])]()
      logicTree.foreachWithStructure(context => if (context.curValue.isInstanceOf[BeBlockParent]) {
        val addToCurBlock = calcToAddToTreeForPos(context.curPosition, context.curValue.asInstanceOf[BeBlockParent], context.childrenValues)
        addToTree.append((context.curPosition, addToCurBlock))
      })
      addToTree.toList
    }
    
    val addToTree = calcToAddToTree()

    var displayTree = logicTree.map(_.asInstanceOf[BeBlock])
    addToTree.foreach(curTuple => {
      curTuple._2.foreach(curChildToAdd => {
        displayTree = displayTree.addChild(curTuple._1, curChildToAdd)
      })
    })

    displayTree
  }

}