package it.evadid.core.datastructures.graph

trait WeightedNeighbourStructure[N, A, E <: Edge[N, A]] extends NeighbourStructure[N, A, E] {

  def getDistFromEdge(edge: E): Double

}
