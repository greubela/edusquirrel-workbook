package it.evadid.evacuation.core.algorithm.routing.model

case class RoutingOption[N](curPos: N, nextStep: Option[N], destination: N, remainingDistance: Double) {
/*
  assert(path.nonEmpty, "path must not be empty!")

  val curPos: N = path.head
  val nextStep: Option[N] = path.tail.headOption
  val destination: N = path.last
*/
  override def toString: String = "RoutingOption for " + curPos + ": over " + nextStep + " to " + destination + " in " + remainingDistance

}
