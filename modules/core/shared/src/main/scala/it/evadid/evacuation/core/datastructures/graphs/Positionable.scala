package it.evadid.evacuation.core.datastructures.graphs

trait Positionable {
  def pos: Position

  def distTo(positionable: Positionable): Double = pos.distTo(positionable.pos)
}

