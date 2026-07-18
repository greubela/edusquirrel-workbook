package it.evadid.evacuation.core.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.Dijkstra.DijkstraInformation
import it.evadid.evacuation.core.algorithm.routing.model.SearchNode


case class Dijkstra[N]() extends Pathfinding[N, DijkstraInformation] {

  override protected def initStartNode(start: N): SearchNode[N, DijkstraInformation] = {
    SearchNode(start, None, DijkstraInformation(0))
  }

  override protected def getOrderingAscending: Ordering[SearchNode[N, DijkstraInformation]] = Ordering.by(_.info.distFromStart)
}

object Dijkstra{
  case class DijkstraInformation(distFromStart: Double)
}