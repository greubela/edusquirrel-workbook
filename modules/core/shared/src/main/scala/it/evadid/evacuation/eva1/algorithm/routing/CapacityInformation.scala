package it.evadid.evacuation.eva1.algorithm.routing

import it.evadid.evacuation.eva1.model.evagraph.EvaPerson

case class CapacityInformation(onPosition: Seq[EvaPerson], maxCapacity: Int)
