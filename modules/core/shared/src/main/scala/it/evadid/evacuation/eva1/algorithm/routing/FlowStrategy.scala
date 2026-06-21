package it.evadid.evacuation.eva1.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.evacuation.eva1.model.evagraph.Router


trait FlowStrategy {

  def decideRouting(allRoutingOptions: Seq[RoutingOption[Router]], freeRoutingOptions: Seq[RoutingOption[Router]]): Option[RoutingOption[Router]]



}
