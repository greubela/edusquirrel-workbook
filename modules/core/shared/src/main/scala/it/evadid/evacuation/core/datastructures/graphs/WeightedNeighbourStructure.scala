package it.evadid.evacuation.core.datastructures.graphs

trait WeightedNeighbourStructure[N, A, E <: Edge[N, A]] extends NeighbourStructure[N, A, E] {

  def getDistFromEdge(edge: E): Double

}
