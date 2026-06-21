package it.evadid.evacuation.eva1.model.evagraph

import it.evadid.evacuation.core.datastructures.graphs.{Position, Positionable}

case class Router(pos: Position, initCapacity: Int, maxCapacity: Int, isExit: Boolean) extends Positionable {
  override def toString: String = "R[" + pos.x + "|" + pos.y + "]"

  def changeX(newVal: Int): Router = new Router(Position(newVal, pos.y), initCapacity, maxCapacity, isExit)

  def changeY(newVal: Int): Router = new Router(Position(pos.x, newVal), initCapacity, maxCapacity, isExit)

  def changeInit(newVal: Int): Router = new Router(Position(pos.x, pos.y), newVal, maxCapacity, isExit)

  def changeMax(newVal: Int): Router = new Router(Position(pos.x, pos.y), initCapacity, newVal, isExit)

  def setExit(newVal: Boolean): Router = new Router(Position(pos.x, pos.y), initCapacity, maxCapacity, newVal)

}

object Router {
  def apply(pos: Position, initCapacity: Int = 0, maxCapacity: Int = 10000, isExit: Boolean = false): Router = new Router(pos, initCapacity, maxCapacity, isExit)

  def apply(x: Double, y: Double): Router = apply(Position(x, y))

  // Todo remove maxCapacity

}