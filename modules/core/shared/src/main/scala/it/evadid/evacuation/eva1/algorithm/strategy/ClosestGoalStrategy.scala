package it.evadid.evacuation.eva1.algorithm.strategy

import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.evacuation.eva1.algorithm.routing.FlowStrategy
import it.evadid.evacuation.eva1.model.evagraph.Router

object ClosestGoalStrategy extends FlowStrategy {


  override def decideRouting(allRoutingOptions: Seq[RoutingOption[Router]], freeRoutingOptions: Seq[RoutingOption[Router]]): Option[RoutingOption[Router]] = {
/*
    println("decideRouting: ")
    println("    allOptions: " + allRoutingOptions)
    println("    freeRoutingOptions: " + freeRoutingOptions)
    println()
*/

    val minFree = freeRoutingOptions.minBy(_.remainingDistance)
    val minTotal = allRoutingOptions.minBy(_.remainingDistance)

    if (minFree.remainingDistance <= minTotal.remainingDistance) {
      Some(minFree)
    } else {
      None
    }

  }
}
