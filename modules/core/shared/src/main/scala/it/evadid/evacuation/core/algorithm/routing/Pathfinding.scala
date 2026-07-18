package it.evadid.evacuation.core.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.model.SearchNode

import scala.collection.mutable

trait Pathfinding[N, I] {

  def shortestPath(graphAccess: GraphAccess[N, I], start: N, dest: N): List[N] = shortestPathSearchNodes(graphAccess, start, dest).map(_.node)

  def shortestPathSearchNodes(graphAccess: GraphAccess[N, I], start: N, dest: N): List[SearchNode[N, I]] = Pathfinding.shortestPathFromMap(dest, shortestPathMap(graphAccess, start, dest))

  def shortestPathMap(graphAccess: GraphAccess[N, I], start: N): Map[N, SearchNode[N, I]] = shortestPathMap(graphAccess, start, None)

  def shortestPathMap(graphAccess: GraphAccess[N, I], start: N, dest: N): Map[N, SearchNode[N, I]] = shortestPathMap(graphAccess, start, Some(dest))

  protected def shortestPathMap(graphAccess: GraphAccess[N, I], start: N, dest: Option[N]): Map[N, SearchNode[N, I]] = {

    val closed = mutable.Map[N, SearchNode[N, I]]()
    val queue = new mutable.PriorityQueue[SearchNode[N, I]]()(using getOrderingAscending.reverse)

    queue.enqueue(initStartNode(start))

    while (queue.nonEmpty && (dest.isEmpty || !closed.contains(dest.get))) {
      val toClose = queue.dequeue()
      if (!closed.contains(toClose.node)) {
        graphAccess.getNeighbours(toClose).toList.filterNot(toAdd => closed.contains(toAdd.node)).foreach(queue.enqueue(_))
        closed.put(toClose.node, toClose)
      }
    }
    closed.toMap
  }

  protected def initStartNode(start: N): SearchNode[N, I]

  protected def getOrderingAscending: math.Ordering[SearchNode[N, I]]

}

object Pathfinding {

  def shortestPathFromMap[N, I](dest: N, map: Map[N, SearchNode[N, I]]): List[SearchNode[N, I]] = {
    @scala.annotation.tailrec
    def go(last: N, path: List[SearchNode[N, I]]): List[SearchNode[N, I]] = {
      val cur = map.get(last)
      if (cur.isEmpty) path
      else {
        val pre = cur.get.predecessor
        if (pre.isEmpty) cur.get :: path
        else go(pre.get, cur.get :: path)
      }
    }

    go(dest, List())
  }

  /**
   * if (map.contains(last)) {
   * val cur = map(last)
   * val pre = cur.predecessor
   * if (cur.predecessor.isEmpty) cur :: path
   * else go(cur.predecessor.get, cur :: path)
   * } else path
   */

}




