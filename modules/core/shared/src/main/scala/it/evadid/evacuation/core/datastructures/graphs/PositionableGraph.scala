package it.evadid.evacuation.core.datastructures.graphs

trait PositionableGraph[N <: Positionable, A, E <: PositionableEdge[N, A]] extends ImmutableGraph[N, A, E] {

}
