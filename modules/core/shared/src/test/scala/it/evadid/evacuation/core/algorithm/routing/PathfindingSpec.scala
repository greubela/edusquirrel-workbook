package it.evadid.evacuation.core.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.Dijkstra.DijkstraInformation
import it.evadid.evacuation.core.algorithm.routing.model.SearchNode
import munit.FunSuite

class PathfindingSpec extends FunSuite {
  private def access(edges: Map[Int, List[(Int, Double)]]) = GraphAccess.fromFunction[Int, DijkstraInformation] { node =>
    edges.getOrElse(node.node, Nil).map { (dest, weight) =>
      SearchNode(dest, Some(node.node), DijkstraInformation(node.info.distFromStart + weight))
    }
  }
  private val wiki = Map(1 -> List(2 -> 7.0, 3 -> 9.0, 6 -> 14.0), 2 -> List(3 -> 10.0, 4 -> 15.0),
    3 -> List(4 -> 11.0, 6 -> 2.0), 4 -> List(5 -> 6.0), 6 -> List(5 -> 9.0))
  test("Dijkstra uses shortest cumulative weight rather than fewest edges") {
    val algorithm = Dijkstra[Int]()
    assertEquals(algorithm.shortestPath(access(wiki), 1, 5), List(1, 3, 6, 5))
    assertEquals(algorithm.shortestPathSearchNodes(access(wiki), 1, 5).last.info.distFromStart, 20.0)
  }
  test("cycles, unreachable nodes and starting at the destination terminate correctly") {
    val graph = access(Map(1 -> List(2 -> 0.0, 3 -> 5.0), 2 -> List(1 -> 0.0, 3 -> 1.0)))
    val algorithm = Dijkstra[Int]()
    assertEquals(algorithm.shortestPath(graph, 1, 3), List(1, 2, 3))
    assertEquals(algorithm.shortestPath(graph, 1, 99), Nil)
    assertEquals(algorithm.shortestPath(graph, 1, 1), List(1))
    assertEquals(algorithm.shortestPathMap(graph, 1).keySet, Set(1, 2, 3))
  }
  test("BFS chooses minimum hops and A-star with zero heuristic agrees with Dijkstra") {
    val bfsAccess = GraphAccess.fromFunction[Int, BFS.BFSInformation] { n =>
      wiki.getOrElse(n.node, Nil).map { (dest, _) => SearchNode(dest, Some(n.node), BFS.BFSInformation(n.info.depth + 1)) }
    }
    val path = BFS[Int]().shortestPath(bfsAccess, 1, 5)
    assertEquals(path.size, 3)
    val astarAccess = GraphAccess.fromFunction[Int, AStar.AStarInformation] { n =>
      wiki.getOrElse(n.node, Nil).map { (dest, cost) => SearchNode(dest, Some(n.node), AStar.AStarInformation(n.info.distFromStart + cost, 0)) }
    }
    assertEquals(AStar[Int](0).shortestPath(astarAccess, 1, 5), List(1, 3, 6, 5))
  }
  test("cached searches reuse a start map and return consistent suffixes") {
    var queries = 0
    val graph = GraphAccess.fromFunction[Int, DijkstraInformation] { n => queries += 1; access(wiki).getNeighbours(n) }
    val cached = CachedPathfinding(graph, Dijkstra[Int]())
    assertEquals(cached.shortestPath(1, 5), List(1, 3, 6, 5))
    val firstQueries = queries
    assertEquals(cached.shortestPath(1, 4), List(1, 3, 4))
    assertEquals(cached.shortestPath(3, 5), List(3, 6, 5))
    assertEquals(queries, firstQueries)
  }
  test("reversed cached paths follow undirected routes and reject directed graphs") {
    val graph = access(Map(1 -> List(2 -> 1.0), 2 -> List(1 -> 1.0, 3 -> 1.0), 3 -> List(2 -> 1.0)))
    val cached = ReveresedCachedPathfinding(graph, Dijkstra[Int](), true)
    assertEquals(cached.shortestPath(1, 3), List(1, 2, 3))
    assertEquals(cached.shortestPath(2, 3), List(2, 3))
    intercept[AssertionError](ReveresedCachedPathfinding(graph, Dijkstra[Int](), false))
  }
  test("path reconstruction rejects broken predecessor chains") {
    val broken = Map(2 -> SearchNode(2, Some(1), DijkstraInformation(3)))
    intercept[IllegalArgumentException](Pathfinding.shortestPathFromMap(2, broken))
  }
  test("path reconstruction rejects cycles and handles long chains without stack overflow") {
    val cycle = Map(1 -> SearchNode(1, Some(2), DijkstraInformation(0)),
      2 -> SearchNode(2, Some(1), DijkstraInformation(0)))
    intercept[IllegalArgumentException](Pathfinding.shortestPathFromMap(2, cycle))
    val chain = (0 to 10000).map(n => n -> SearchNode(n, Option.when(n > 0)(n - 1), DijkstraInformation(n))).toMap
    assertEquals(Pathfinding.shortestPathFromMap(10000, chain).map(_.node), (0 to 10000).toList)
  }

}
