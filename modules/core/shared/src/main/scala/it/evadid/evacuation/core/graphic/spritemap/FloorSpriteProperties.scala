package it.evadid.evacuation.core.graphic.spritemap

import it.evadid.evacuation.core.datastructures.matrix.MatrixPosition

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


case class FloorSpriteProperties(isFreeTop: Boolean, isFreeTopLeft: Boolean, isFreeLeft: Boolean, isFreeBottomLeft: Boolean, isFreeBottom: Boolean, isFreeBottomRight: Boolean, isFreeRight: Boolean, isFreeTopRight: Boolean) {

  private val reachableFromCenter: Set[MatrixPosition] = {
    val buf = ListBuffer.empty[MatrixPosition]
    if (isFreeTop) buf += MatrixPosition(0, -1)
    if (isFreeTopLeft) buf += MatrixPosition(-1, -1)
    if (isFreeLeft) buf += MatrixPosition(-1, 0)
    if (isFreeBottomLeft) buf += MatrixPosition(-1, 1)
    if (isFreeBottom) buf += MatrixPosition(0, 1)
    if (isFreeBottomRight) buf += MatrixPosition(1, 1)
    if (isFreeRight) buf += MatrixPosition(1, 0)
    if (isFreeTopRight) buf += MatrixPosition(1, -1)
    buf += MatrixPosition(0, 0)
    buf.toSet
  }

  def reachableFrom(matrixPosition: MatrixPosition): Set[MatrixPosition] =
    reachableFromCenter.map(old => MatrixPosition(old.x + matrixPosition.x, old.y + matrixPosition.y))

  override val toString: String = {
    "TSP(" +
      (if (isFreeTop) "T" else "_") +
      (if (isFreeTopLeft) "↖" else "_") +
      (if (isFreeLeft) "L" else "_") +
      (if (isFreeBottomLeft) "↙" else "_") +
      (if (isFreeBottom) "B" else "_") +
      (if (isFreeBottomRight) "↘" else "_") +
      (if (isFreeRight) "R" else "_") +
      (if (isFreeTopRight) "↗" else "_") +
      ")"
  }

  def isFullyOpen(): Boolean = isFreeTop && isFreeTopLeft && isFreeLeft && isFreeBottomLeft && isFreeBottom && isFreeBottomRight && isFreeRight && isFreeTopRight

  def isFullyClosed(): Boolean = !isFreeTop && !isFreeTopLeft && !isFreeLeft && !isFreeBottomLeft && !isFreeBottom && !isFreeBottomRight && !isFreeRight && !isFreeTopRight

  def isOneClosed(): Boolean = !isFreeTop || !isFreeTopLeft || !isFreeLeft || !isFreeBottomLeft || !isFreeBottom || !isFreeBottomRight || !isFreeRight || !isFreeTopRight

}

object FloorSpriteProperties {

  val closed: FloorSpriteProperties = FloorSpriteProperties(false, false, false, false, false, false, false, false)
  val open: FloorSpriteProperties = FloorSpriteProperties(true, true, true, true, true, true, true, true)

  private var objCache = mutable.HashMap[String, FloorSpriteProperties]()

  def apply(str: String): Option[FloorSpriteProperties] = {
    if (!objCache.contains(str)) {
      val res =
        if (str.length() != 8 || !"[01]*".r.matches(str)) Option.empty
        else Some(FloorSpriteProperties(
          isFreeTop = str.charAt(0) == '1',
          isFreeTopLeft = str.charAt(1) == '1',
          isFreeLeft = str.charAt(2) == '1',
          isFreeBottomLeft = str.charAt(3) == '1',
          isFreeBottom = str.charAt(4) == '1',
          isFreeBottomRight = str.charAt(5) == '1',
          isFreeRight = str.charAt(6) == '1',
          isFreeTopRight = str.charAt(7) == '1'
        ))
      res.foreach(objCache.put(str, _))
    }
    objCache.get(str)

  }


}