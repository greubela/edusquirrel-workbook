package it.evadid.evacuation.core.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.core.datastructures.graph.{Edge, WeightedNeighbourStructure}
import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList

import scala.collection.mutable

class RoutingOptionsToDestinationCalculator {

  def routingOptionsMap[N, I, E <: Edge[N, I]](graph: WeightedNeighbourStructure[N, I, E], destination: N): MultiHashMapList[N, RoutingOption[N]] = {
    executeDijkstra(graph, destination) //.getCopyWithApplied(op => RoutingOption(op.path.reverse, op.remainingDistance))
  }

  // must be bidirectional
  private def executeDijkstra[N, I, E <: Edge[N, I]](graph: WeightedNeighbourStructure[N, I, E], startPoint: N): MultiHashMapList[N, RoutingOption[N]] = {

    val paths: MultiHashMapList[N, RoutingOption[N]] = new MultiHashMapList[N, RoutingOption[N]]()

    val closedEdges = mutable.HashSet[Edge[N, I]]()

    val distOrdering: Ordering[RoutingOption[N]] = Ordering.by(_.remainingDistance)
    val queue = new mutable.PriorityQueue[RoutingOption[N]]()(distOrdering.reverse)

    queue += RoutingOption[N](startPoint, None, startPoint, 0)

    while (queue.nonEmpty) {
      val toClose = queue.dequeue()
      val closedNode = toClose.curPos
      paths.addElement((closedNode, toClose))

      //println("toClose: " + toClose)
      //println("----  queue (before): " + queue)

      val neighbours = graph.getNeighbours(closedNode)
      //println("#### neighbours: " + neighbours)
      neighbours.foreach(curNeighbour => {
        //println("    neighbour: " + curNeighbour)
        // Do not extend if: a) edge would create a cycle b) edge was already closed. last one ignored
        if (!closedEdges.contains(curNeighbour)) { //} && !toClose.path.contains(curNeighbour.dest)) {
          closedEdges += curNeighbour

          val dist = graph.getDistFromEdge(curNeighbour)
          val newOption = RoutingOption(curNeighbour.dest, Some(curNeighbour.start), startPoint, toClose.remainingDistance + dist)
          queue += newOption
        }
      })

    //  println("----  queue  (after): " + queue + "\n")
    }

    paths
  }

}

object RoutingOptionsToDestinationCalculator {


  def main(args: Array[String]): Unit = {

    val wg = new WeightedNeighbourStructure[String, Double, Edge[String, Double]]() {
      override def getDistFromEdge(edge: Edge[String, Double]): Double = edge.content

      val edges1: List[Edge[String, Double]] = List(Edge("1", "2", 2), Edge("1", "3", 3.5))
      val edges2: List[Edge[String, Double]] = List(Edge("2", "4", 4), Edge("2", "1", 2), Edge("2", "3", 1))
      val edges3: List[Edge[String, Double]] = List(Edge("3", "1", 3.5), Edge("3", "4", 5), Edge("3", "2", 1))
      val edges4: List[Edge[String, Double]] = List(Edge("4", "2", 4), Edge("4", "3", 5))

      override def getNeighbours(n: String): List[Edge[String, Double]] = {

        if (n == "1") edges1
        else if (n == "2") edges2
        else if (n == "3") edges3
        else if (n == "4") edges4
        else null
      }

    }

    val calculator = new RoutingOptionsToDestinationCalculator()
    val res = calculator.routingOptionsMap(wg, "1")
    res.print()

  }

}