package it.evadid.core.datastructures.graph

trait Graph[N, A, E <: Edge[N, A]] extends NeighbourStructure[N, A, E] {

  def nodes: Seq[N]

  def edges: Seq[E]

  def getEdgesTo(n: N): Seq[E]

  def allEdgesBetween(n1: N, n2: N): Seq[E] = dirEdgesBetween(n1, n2) ++ dirEdgesBetween(n2, n1)

  def dirEdgesBetween(start: N, dest: N): Seq[E] = edges.filter(e => e.start == start && e.dest == dest)

  override def toString(): String = "Graph (" + getClass + ") with " + nodes.size + " nodes and " + edges.size + " edges (hc " + hashCode() + ")"

}
