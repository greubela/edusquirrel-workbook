package it.evadid.core.datastructures.graph

trait ImmutableGraph[N, A, E <: Edge[N, A]] extends Graph[N, A, E] {

  def addNode(n: N): this.type

  def addEdge(start: N, dest: N, info: A, directed: Boolean = false): this.type

  def addEdgeDirected(start: N, dest: N, info: A): this.type = addEdge(start, dest, info, true)

  def clearEdges(): this.type

  def clear(): this.type

  def replaceNode(oldNode: N, newNode: N): this.type

  def deleteNodeAndEdges(oldNode: N): this.type

  def deleteEdgesForNode(node: N): this.type

  def deleteEdgesBetween(n1: N, n2: N, directed: Boolean): this.type


}
