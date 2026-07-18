package it.evadid.evacuation.core.datastructures.graphs

import it.evadid.core.datastructures.graph.Positionable

import scala.language.implicitConversions


case class Position(x: Int, y: Int) extends Positionable {
  def distTo(pos2: Position): Double = Position.euclidianDistanceBetween(this, pos2)

  def addVector(p2: Position, scale: Double = 1): Position = Position(x + p2.x * scale, y + p2.y * scale)

  def pointBetween(p2: Position, t: Double = 1): Position = Position(x + (p2.x - x) * t, y + (p2.y - y) * t)

  override def pos: Position = this
}

object Position {

  implicit def intTupToPos(pos: (Int, Int)): Position = Position(pos._1, pos._2)

  implicit def dubTupToPos(pos: (Double, Double)): Position = Position(pos._1, pos._2)

  def between(start: Position, dest: Position, percent: Double = 0.5): Position = {
    val xDist = dest.x - start.x
    val yDist = dest.y - start.y
    Position(start.x + percent * xDist, start.y + percent * yDist)
  }

  def euclidianDistanceBetween(pos1: Position, pos2: Position): Double = {
    val dx = pos1.x - pos2.x
    val dy = pos1.y - pos2.y
    Math.sqrt(dx * dx + dy * dy)
  }

  def apply(x: Double, y: Double) = new Position(Math.round(x).asInstanceOf[Int], Math.round(y).asInstanceOf[Int])

  def getNearestElement[T <: Positionable](list: Seq[T], x: Double, y: Double): (T, Double) = getNearestElement(list, Position(x, y))

  def getNearestElements[T <: Positionable](list: Seq[T], x: Double, y: Double, n: Int): Seq[(T, Double)] = getNearestElements(list, Position(x, y), n)

  def getNearestElement[T](list: Seq[T], pos: Position, posExtractor: T => Position): (T, Double) = getNearestElements(list, pos, posExtractor, 1).head


  def getNearestElements[T](list: Seq[T], pos: Position, posExtractor: T => Position, n: Int): Seq[(T, Double)] = {
    case class Wrapper[T2](obj: T2, pos: Position) extends Positionable
    val wrappedSeq: Seq[Wrapper[T]] = list.map(obj => Wrapper(obj, posExtractor(obj)))
    val nearestElementSeq: Seq[(Wrapper[T], Double)] = getNearestElements(wrappedSeq, pos, n)

    def unwrapFunc: (Wrapper[T], Double) => (T, Double) = (wrapped, dist) => (wrapped.obj, dist)

    nearestElementSeq.map(tup => unwrapFunc(tup._1, tup._2))
  }

  def getNearestElements[T <: Positionable](list: Seq[T], pos: Position, n: Int): Seq[(T, Double)] = {
    if (n == 0) Seq()
    else {
      val (nearest, dist) = getNearestElement(list, pos)
      if (n <= 1) Seq((nearest, dist))
      else {
        Seq((nearest, dist)) ++ getNearestElements(list.toList.filterNot(_ == nearest), pos.x, pos.y, n - 1)
      }
    }
  }

  def getNearestElement[T <: Positionable](list: Seq[T], pos: Position): (T, Double) = list.map(obj => (obj, obj.pos.distTo(pos))).minBy(_._1.pos.distTo(pos))


}
