package contentmanagement.datastructures.tree

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition
import util.{CodeStringBuilder, FunctionalUtility}

import scala.collection.mutable


trait Tree[P <: TreePosition, D] {

  def rootPosition: P

  def getData(position: P): Option[D]

  def getParent(position: P): Option[P]

  def getChildren(position: P): List[P]

  def addAsChildNr(Position: P, childNr: Int, newData: D): Tree[P, D]

  def addAsLastChild(position: P, newData: D): Tree[P, D]

  def addSubtreeAsLastChild(position: P, subtree: Tree[P, D]): Tree[P, D]

  def addSubtreeAsChildNr(insertAtPosition: P, childNr: Int, subtree: Tree[P, D]): Tree[P, D]

  def traverseStructureAndAddChildren(calcChildrenToAdd: TSC => List[(Int, D)], childrenToAddToRoot: List[(Int, D)] = List()): Tree[P, D]

  def subtreeInclPosition(position: P): Tree[P, D]

  def removePosition(position: P): Tree[P, D]

  def searchForValue(value: D): Set[P] = entries.filter(_._2 == value).map(_._1)

  def getSubtreeInclLevel(keepInclLevel: Int): Tree[P, D]

  def values: Set[D] = entries.map(_._2)

  def isEmpty: Boolean

  def entries: Set[(P, D)] = if (isEmpty) Set() else {
    val result = mutable.Set[(P, D)]()
    foreach((position, value) => result.add((position, value)))
    result.toSet
  }

  def size: Int = if (isEmpty) 0 else entries.size

  private def levelString: String = {
    val builder = CodeStringBuilder()
    foreach((pos, data) => builder.setIntLevel(pos.level).appendNextLine(data.toString), false)
    builder.toString
  }

  override def toString: String = "Tree with " + size + " entries:" + levelString


  def mapWithStructure[O](transformData: TSC => O): Tree[P, O]

  def foreachWithStructure(consumer: TreeStructureContext[P, D] => Any, bottomUp: Boolean = true): Unit = mapWithStructure(consumer)

  def foreach(consumer: (P, D) => Any, bottomUp: Boolean = true): Unit = foreachWithStructure(func => consumer(func.curPosition, func.curValue), bottomUp)

  def map[O](function: D => O): Tree[P, O] = mapWithStructure(info => function(info.curValue))

  type TSC = TreeStructureContext[P, D]
  type TEC[O] = TreeFunctorExecutionContext[P, D, O]
  type TSEC[O] = TreeStructureAndExecutionContext[P, D, O]

  def mapWithEnrichedContext[O, C <: TSEC[O]](function: C => O, enrichContext: TSEC[O] => C): Tree[P, O] = mapWithContext(context => function(enrichContext(context)))

  def mapWithContext[O](function: TSEC[O] => O): Tree[P, O] = {

    def getExecutionContextInfo(curInfo: TSC, curCache: TSC => O): TSEC[O] = new TreeStructureAndExecutionContextImpl[P, D, O](curInfo, new TEC[O]() {
      override def accessOtherResult(otherPosition: TSC): O = curCache(otherPosition)
    })

    val func: ((TSC, TSC => O) => O) = (curInfo, curCache) => function(getExecutionContextInfo(curInfo, curCache))

    val funcCached: (TSC => O) = FunctionalUtility.withCacheAndResolvedDependencies(func)
    mapWithStructure(funcCached)
  }

  def applyWithChildResults[O](callFunc: (TreeStructureContext[P, D], Map[D, O]) => O): Map[NodeBasedTreePosition, O]

}

