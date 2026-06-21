package it.evadid.evacuation.eva1.algorithm.strategy

import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.evacuation.eva1.algorithm.routing.FlowStrategy
import it.evadid.evacuation.eva1.model.evagraph.Router

class MultipleGoalStrategy(threshold: Double = 1.3) extends FlowStrategy{
  override def decideRouting(allRoutingOptions: Seq[RoutingOption[Router]], freeRoutingOptions: Seq[RoutingOption[Router]]): Option[RoutingOption[Router]] = {
    val minFree = freeRoutingOptions.minBy(_.remainingDistance)
    val minTotal = allRoutingOptions.minBy(_.remainingDistance)

    if (minFree.remainingDistance <= minTotal.remainingDistance * threshold) {
      Some(minFree)
    } else {
      None
    }
  }
}
