package it.evadid.evacuation.core.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.model.SearchNode

trait GraphAccess[N, I] {
  def getNeighbours(searchNode: SearchNode[N, I]): Seq[SearchNode[N, I]]
}

object GraphAccess {

  def fromFunction[N, I](func: SearchNode[N, I] => Seq[SearchNode[N, I]]): GraphAccess[N, I] = new GraphAccess[N, I]() {
    override def getNeighbours(searchNode: SearchNode[N, I]): Seq[SearchNode[N, I]] = func.apply(searchNode)
  }

}