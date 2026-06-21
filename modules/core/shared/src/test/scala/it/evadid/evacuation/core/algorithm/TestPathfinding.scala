package it.evadid.evacuation.core.algorithm

import it.evadid.evacuation.core.algorithm.routing.Dijkstra.DijkstraInformation
import it.evadid.evacuation.core.algorithm.routing.model.SearchNode
import it.evadid.evacuation.core.algorithm.routing.{Dijkstra, GraphAccess}
import it.evadid.core.datastructures.graph._
import it.evadid.evacuation.core.datastructures.graphs.Position

object TestPathfinding {


  def main(args: Array[String]): Unit = {
    testHalfMindGraph()

    println("|||\n\n\n|||")
    testWikiDijkstraGraph()
  }

  def testWikiDijkstraGraph(): Unit = {
    // Graph Vis: https://en.wikipedia.org/wiki/Dijkstra%27s_algorithm

    val graph = new ObservableGraph[Integer, Integer]()

    graph += (1, 2, 7)
    graph += (1, 3, 9)
    graph += (1, 6, 14)

    graph += (2, 3, 10)
    graph += (2, 4, 15)

    graph += (3, 4, 11)
    graph += (3, 6, 2)

    graph += (4, 5, 6)

    graph += (5, 6, 9)

    val graphAccess: GraphAccess[Integer, DijkstraInformation] = new GraphAccess[Integer, DijkstraInformation] {
      override def getNeighbours(searchNode: SearchNode[Integer, DijkstraInformation]): Seq[SearchNode[Integer, DijkstraInformation]] = {
        graph.getNeighbours(searchNode.node).map(edge => SearchNode(edge.dest, Some(edge.start), DijkstraInformation(edge.content + searchNode.info.distFromStart)))
      }
    }

    val path = new Dijkstra[Integer]().shortestPath(graphAccess, 1, 5)

    println("path: " + path)

  }

  def testHalfMindGraph(): Unit = {

    val graph = new ObservableGraph[Position, Integer]()

    graph += ((620 / 2, 160 / 2), (560 / 2, 160 / 2), 1)
    graph += ((620 / 2, 160 / 2), (620 / 2, 215 / 2), 1)
    graph += ((620 / 2, 215 / 2), (675 / 2, 200 / 2), 1)
    graph += ((620 / 2, 215 / 2), (675 / 2, 240 / 2), 1)
    graph += ((620 / 2, 215 / 2), (620 / 2, 410 / 2), 1)
    graph += ((620 / 2, 410 / 2), (560 / 2, 410 / 2), 1)
    graph += ((620 / 2, 410 / 2), (560 / 2, 450 / 2), 1)
    graph += ((620 / 2, 410 / 2), (560 / 2, 450 / 2), 1)
    graph += ((620 / 2, 410 / 2), (620 / 2, 520 / 2), 1)

    // left (purple)
    graph += ((475 / 2, 660 / 2), (620 / 2, 520 / 2), 1)
    graph += ((475 / 2, 660 / 2), (435 / 2, 630 / 2), 1)
    graph += ((475 / 2, 660 / 2), (440 / 2, 700 / 2), 1)
    graph += ((440 / 2, 700 / 2), (485 / 2, 720 / 2), 1)
    graph += ((440 / 2, 700 / 2), (450 / 2, 750 / 2), 1)
    graph += ((440 / 2, 700 / 2), (290 / 2, 850 / 2), 1)

    graph += ((290 / 2, 850 / 2), (250 / 2, 800 / 2), 1)
    graph += ((290 / 2, 850 / 2), (250 / 2, 800 / 2), 1)
    graph += ((290 / 2, 850 / 2), (250 / 2, 880 / 2), 1)

    graph += ((250 / 2, 880 / 2), (285 / 2, 920 / 2), 1)

    // right (blue)
    graph += ((650 / 2, 490 / 2), (620 / 2, 520 / 2), 1)

    graph += ((735 / 2, 520 / 2), (620 / 2, 520 / 2), 1)
    graph += ((735 / 2, 520 / 2), (710 / 2, 570 / 2), 1)
    graph += ((735 / 2, 520 / 2), (750 / 2, 570 / 2), 1)

    graph += ((888 / 2, 520 / 2), (735 / 2, 520 / 2), 1)
    graph += ((888 / 2, 520 / 2), (870 / 2, 480 / 2), 1)
    graph += ((888 / 2, 520 / 2), (900 / 2, 480 / 2), 1)
    graph += ((888 / 2, 520 / 2), (1050 / 2, 520 / 2), 1)

    graph += ((1050 / 2, 520 / 2), (1020 / 2, 560 / 2), 1)
    graph += ((1050 / 2, 520 / 2), (1060 / 2, 560 / 2), 1)
    graph += ((1050 / 2, 520 / 2), (1190 / 2, 520 / 2), 1)
    graph += ((1190 / 2, 520 / 2), (1190 / 2, 470 / 2), 1)

    // escape (green)

    graph += ((1190 / 2, 520 / 2), (1250 / 2, 520 / 2), 1)
    graph += ((250 / 2, 880 / 2), (130 / 2, 1000 / 2), 1)
    graph += ((620 / 2, 160 / 2), (620 / 2, 40 / 2), 1)

    val graphAccess: GraphAccess[Position, DijkstraInformation] = GraphAccess.fromFunction(
      searchNode => graph.getNeighbours(searchNode.node).map(
        edge => new SearchNode(edge.dest, Some(edge.start), new DijkstraInformation(edge.content + searchNode.info.distFromStart))
      ))

    println("--- beginnen")
    val path = new Dijkstra[Position]().shortestPath(graphAccess, (310, 80), (595, 235))
    println("--- enden")


    println("path: " + path.mkString("\n\n\n", "\n-> ", "\n---<<"))

  }


}
