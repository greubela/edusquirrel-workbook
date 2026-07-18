package it.evadid.evacuation.core.algorithm.routing


import it.evadid.evacuation.core.algorithm.routing.model.SearchNode

import scala.collection.mutable

case class ReveresedCachedPathfinding[N, I](graphAccess: GraphAccess[N, I], algorithm: Pathfinding[N, I], graphIsUndirected: Boolean) {

  assert(graphIsUndirected, "Reveresed Cached Pathfinding only works on undirected graphs!")

  private val shortestPathMaps: mutable.Map[N, Map[N, SearchNode[N, I]]] = new mutable.HashMap[N, Map[N, SearchNode[N, I]]]()
  private val shortestPaths: mutable.Map[(N, N), List[SearchNode[N, I]]] = new mutable.HashMap[(N, N), List[SearchNode[N, I]]]()

  def shortestPath(start: N, dest: N): List[N] = shortestPathSearchNodes(start, dest).map(_.node)


  // Todo: Performance Analysis for additional mapping (start,dest) -> path @Todo
  def shortestPathSearchNodes(start: N, dest: N): List[SearchNode[N, I]] = {
    val tup = (start, dest)

    if(shortestPaths.contains( tup )){
      shortestPaths(tup)
    }else{
      if (!shortestPathMaps.contains(dest)) {
        val map = algorithm.shortestPathMap(graphAccess, dest)
        shortestPathMaps.put(dest, map)
      }
      val calculatedRes = Pathfinding.shortestPathFromMap(start, shortestPathMaps(dest)).reverse
      shortestPaths.put(tup, calculatedRes)
      calculatedRes
    }
  }

}
