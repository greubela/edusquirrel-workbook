package it.evadid.core.datastructures.graph

trait PositionableGraph[N <: Positionable, A, E <: PositionableEdge[N, A]] extends ImmutableGraph[N, A, E] {

}
