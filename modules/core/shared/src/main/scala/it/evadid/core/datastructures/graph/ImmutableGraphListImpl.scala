package it.evadid.core.datastructures.graph

import scala.collection.mutable

abstract class ImmutableGraphListImpl[N, A, E <: Edge[N, A]](nodeList: List[N], edgeList: List[E]) extends ImmutableGraph[N, A, E] {

  override val nodes: Seq[N] = nodeList

  override def edges: Seq[E] = edgeList

  def createEdge(start: N, dest: N, info: A): E

  override def addNode(n: N): this.type =
    if (nodeList.contains(n)) this
    else createInstance(nodeList ++ List(n), edgeList)

  override def addEdge(start: N, dest: N, info: A, directed: Boolean): this.type = {

    val addNodes = mutable.ListBuffer[N]()
    if (!nodeList.contains(start)) addNodes += start
    if (!nodeList.contains(dest) && dest != start) addNodes += dest

    val addEdges = mutable.ListBuffer[E]()
    addEdges += createEdge(start, dest, info)

    if (!directed && start != dest) addEdges += createEdge(dest, start, info)

    createInstance(nodeList ++ addNodes, edgeList ++ addEdges)

  }

  override def clearEdges(): this.type = createInstance(nodeList, List())

  override def clear(): this.type = createInstance(List(), List())

  override def replaceNode(oldNode: N, newNode: N): this.type = {

    if (!nodeList.contains(oldNode)) {
      this
    } else {
      val newNodes = nodeList.updated(nodeList.indexOf(oldNode), newNode).distinct
      val newEdges = edgeList.map { edge =>
        if (edge.start == oldNode || edge.dest == oldNode)
          createEdge(if (edge.start == oldNode) newNode else edge.start,
            if (edge.dest == oldNode) newNode else edge.dest, edge.content)
        else edge
      }

      createInstance(newNodes, newEdges)
    }

  }

  override def deleteNodeAndEdges(oldNode: N): this.type = {
    if (!nodeList.contains(oldNode)) {
      this
    } else {
      val newNodes = nodeList.filter(_ != oldNode)
      val newEdges = edgeList.filter(e => e.start != oldNode && e.dest != oldNode)
      createInstance(newNodes, newEdges)
    }
  }

  def deleteEdgesBetween(n1: N, n2: N, directed: Boolean = false): this.type = {
    val toRemove = if (directed) dirEdgesBetween(n1, n2) else allEdgesBetween(n1, n2)
    val edgesNew = edges.filter(!toRemove.contains(_))
    createInstance(nodes, edgesNew)
  }

  override def deleteEdgesForNode(node: N): this.type =
    createInstance(nodeList, edgeList.filter(e => e.start != node && e.dest != node))

  override def getNeighbours(n: N): Seq[E] = edgeList.filter(_.start == n)

  override def getEdgesTo(n: N): Seq[E] = edgeList.filter(_.dest == n)

  protected def createInstance(nodes: Seq[N], edges: Seq[E]): this.type

}
