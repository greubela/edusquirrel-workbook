package contentmanagement.datastructures.tree

import util.CodeStringBuilder

import scala.collection.mutable


trait Tree[D, P <: TreePosition] {

  def rootPosition: P

  def getData(position: P): Option[D]

  def getParent(position: P): Option[P]

  def getChildren(position: P): List[P]

  def addChild(position: P, newData: D): Tree[D, P]

  def removePosition(position: P): Tree[D, P]

  def searchForValue(value: D): Set[P] = entries.filter(_._2 == value).map(_._1)

  def map[O](function: D => O): Tree[O, P]

  def mapWithContext[O](function: (D, ExecutionContextInfo[P, D, O]) => O): Tree[O, P]
  
  def foreach(consumer: D => Any, bottomUp: Boolean): Unit = foreach((pos, value) => consumer(value), bottomUp)

  def foreach(consumer: (P, D) => Any, bottomUp: Boolean = true): Unit

  def values: Set[D] = entries.map(_._2)

  def isEmpty: Boolean

  def entries: Set[(P, D)] = if (isEmpty) Set() else {
    val result = mutable.Set[(P, D)]()
    foreach((position, value) => result.add((position, value)))
    result.toSet
  }

  def size: Int = if(isEmpty) 0 else entries.size

  private def levelString: String = {
      val builder = CodeStringBuilder()
      foreach( (pos, data) => builder.setIntLevel(pos.level).appendNextLine(data.toString) , false)
      builder.toString
  }

  override def toString: String = "Tree with " + size + " entries:" + levelString
}

