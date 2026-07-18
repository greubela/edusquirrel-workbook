package it.evadid.core.datastructures.graph

trait NeighbourStructure[N, A, E <: Edge[N, A]] {

  def getNeighbours(n: N): Seq[E]

}
