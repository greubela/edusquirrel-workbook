package it.evadid.evacuation.core.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.AStar.AStarInformation
import it.evadid.evacuation.core.algorithm.routing.model.SearchNode


case class AStar[N](estDistStartDest: Double) extends Pathfinding[N, AStarInformation] {
  override def initStartNode(start: N): SearchNode[N, AStarInformation] = SearchNode(start, None, AStarInformation(0, estDistStartDest))

  override def getOrderingAscending: Ordering[SearchNode[N, AStarInformation]] = Ordering.by(sn => sn.info.distFromStart + sn.info.estDistToDest)
}

object AStar{

  case class AStarInformation(distFromStart: Double, estDistToDest: Double)
}