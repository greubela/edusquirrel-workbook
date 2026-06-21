package it.evadid.evacuation.core.datastructures.graphs

class PositionableEdge[N <: Positionable, A](start: N, dest: N,  info: A) extends Edge[N, A](start, dest, info) with Positionable {
  override def pos: Position = start.pos.pointBetween(dest.pos, 0.5)

  def pxDist: Double = start.distTo(dest)

}
