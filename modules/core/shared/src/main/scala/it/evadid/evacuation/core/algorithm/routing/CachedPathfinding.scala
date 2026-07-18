package it.evadid.evacuation.core.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.model.SearchNode

import scala.collection.mutable

case class CachedPathfinding[N, I](graphAccess: GraphAccess[N, I], algorithm: Pathfinding[N, I]) {

/*
  private val maxId = 0
  private val nodeIds = Map[N, Int]

  private val shortestPathLists: mutable.Map[N, mutable.ArrayBuffer[SearchNode[N, I]]] = new mutable.HashMap()
*/
  private val shortestPathMaps: mutable.Map[N, Map[N, SearchNode[N, I]]] = new mutable.HashMap[N, Map[N, SearchNode[N, I]]]()

  private val shortestPaths: mutable.Map[(N, N), List[N]] = new mutable.HashMap[(N, N), List[N]]()


  private def storePathToMap(path: List[N]): Unit = {
    if (path.nonEmpty) {
      shortestPaths.put((path.head, path.last), path)
      storePathToMap(path.tail)
    }
  }

  def shortestPath(start: N, dest: N): List[N] = {


    if (shortestPaths.contains((start, dest))) {
      shortestPaths((start, dest))
    } else {
      val res = shortestPathSearchNodes(start, dest).map(_.node)
      storePathToMap(res)
      res
    }
  }

  def shortestPathSearchNodes(start: N, dest: N): List[SearchNode[N, I]] = {

    if (!shortestPathMaps.contains(start)) {
      val map = algorithm.shortestPathMap(graphAccess, start)
      shortestPathMaps.put(start, map)
    }

    Pathfinding.shortestPathFromMap(dest, shortestPathMaps(start))

  }

}
