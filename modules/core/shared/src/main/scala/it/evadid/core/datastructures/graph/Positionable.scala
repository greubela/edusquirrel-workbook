package it.evadid.core.datastructures.graph

import it.evadid.evacuation.core.datastructures.graphs.Position

trait Positionable {
  def pos: Position

  def distTo(positionable: Positionable): Double = pos.distTo(positionable.pos)
}

