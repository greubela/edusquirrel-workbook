package it.evadid.core.datastructures.graph

import it.evadid.evacuation.core.datastructures.seqs.{MutableObservableSeq, ObservableSeq}

import scala.collection._

class ObservableGraph[N, A] extends Graph[N, A, Edge[N, A]] {

  var nodes: MutableObservableSeq[N] = new MutableObservableSeq[N]()
  private var edgesInternal: MutableObservableSeq[Edge[N, A]] = new MutableObservableSeq[Edge[N, A]]

  def edges: ObservableSeq[Edge[N, A]] = edgesInternal

  edgesInternal.addAddedListener(edge => {
    if (!nodes.contains(edge.start))
      nodes += edge.start
    if (!nodes.contains(edge.dest))
      nodes += edge.dest
  })

  nodes.addRemovedListener(
    node => {
      edgesInternal.toList.filter(_.start == node).foreach(edgesInternal -= _)
      edgesInternal.toList.filter(_.dest == node).foreach(edgesInternal -= _)
    }
  )

  protected def clearEdges(): Unit = edgesInternal.foreach(edgesInternal -= _)

  def getEdges(start: N, dest: N): List[Edge[N, A]] = edgesInternal.filter(curEdge => curEdge.start == start && curEdge.dest == dest).toList

  def getReverseEdges(edge: Edge[N, A]): Seq[Edge[N, A]] = getEdges(edge.dest, edge.start)


  def +=>(start: N, dest: N)(implicit contentFactory: (N, N) => A): Unit = {
    this +=> (start, dest, contentFactory.apply(start, dest))
  }

  def +=(start: N, dest: N)(implicit contentFactory: (N, N) => A): Unit = {
    this += (start, dest, contentFactory.apply(start, dest))
  }

  def +=(edge: Edge[N, A]): Unit = {
    +=>(edge.start, edge.dest, edge.content)
    +=>(edge.dest, edge.start, edge.content)
  }

  def +=>(edge: Edge[N, A]): Unit = {
    +=>(edge.start, edge.dest, edge.content)
  }

  def +=(start: N, dest: N, content: A): Unit = {
    +=>(start, dest, content)
    +=>(dest, start, content)
  }

  def +=>(start: N, dest: N, content: A): Unit = {
    val connectingEdges = edgesInternal.filter(edge => edge.start == start && edge.dest == dest)
    connectingEdges.foreach(edgesInternal.-=)
    edgesInternal += Edge(start, dest, content)
  }

  def -=>(start: N, dest: N): Unit = getEdges(start, dest).foreach(edgesInternal.-=(_))

  def -=(start: N, dest: N): Unit = {
    -=>(start, dest)
    -=>(dest, start)
  }

  def getNeighbours(n: N): immutable.List[Edge[N, A]] = edgesInternal.toList.filter(_.start == n)

  override def toString: String = "ObservableGraph with " + nodes.size + " nodes and " + edgesInternal.size + " edges (location: " + super.toString + ")"

  def replaceNode(oldNode: N, newNode: N): Unit = {
    val edgesWithStart = edgesInternal.filter(_.start == oldNode)
    val edgesWithDest = edgesInternal.filter(_.dest == oldNode)

    edgesWithStart.foreach(edgesInternal -= _)
    edgesWithDest.foreach(edgesInternal -= _)

    nodes -= oldNode // Edges will be removed automatically! (???)

    nodes += newNode

    edgesWithStart.foreach(edge => this += (newNode, edge.dest, edge.content))
    edgesWithDest.foreach(edge => this += (edge.start, newNode, edge.content))
  }

  override def getEdgesTo(n: N): scala.Seq[Edge[N, A]] = edges.filter(_.dest == n).toList
}

