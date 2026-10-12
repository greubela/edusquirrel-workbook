package it.evadid.core.datastructures.graph

import it.evadid.evacuation.core.datastructures.graphs.Position
import upickle.default.*

class PositionableEdge[N <: Positionable, A](start: N, dest: N,  info: A) extends Edge[N, A](start, dest, info) with Positionable {
  override def pos: Position = start.pos.pointBetween(dest.pos, 0.5)

  def pxDist: Double = start.distTo(dest)

}

object PositionableEdge {
  given [N <: Positionable: ReadWriter, A: ReadWriter]: ReadWriter[PositionableEdge[N, A]] =
    readwriter[Edge[N, A]].bimap(
      edge => Edge(edge.start, edge.dest, edge.content),
      edge => new PositionableEdge(edge.start, edge.dest, edge.content))
}
