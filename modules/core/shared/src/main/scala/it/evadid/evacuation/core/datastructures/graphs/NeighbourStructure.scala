package it.evadid.evacuation.core.datastructures.graphs

trait NeighbourStructure[N, A, E <: Edge[N, A]] {

  def getNeighbours(n: N): Seq[E]

}
