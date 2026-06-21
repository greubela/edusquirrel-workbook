package it.evadid.evacuation.core.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.BFS.BFSInformation
import it.evadid.evacuation.core.algorithm.routing.model.SearchNode


case class BFS[N]() extends Pathfinding[N, BFSInformation] {
  override def initStartNode(start: N): SearchNode[N, BFSInformation] = {
    SearchNode(start, None, BFSInformation(0))
  }

  override def getOrderingAscending: Ordering[SearchNode[N, BFSInformation]] = Ordering.by(_.info.depth)
}

object BFS {

  case class BFSInformation(depth: Int)

}