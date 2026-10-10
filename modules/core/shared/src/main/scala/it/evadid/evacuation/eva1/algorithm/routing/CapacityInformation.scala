package it.evadid.evacuation.eva1.algorithm.routing

import upickle.default.ReadWriter

import it.evadid.evacuation.eva1.model.evagraph.EvaPerson

case class CapacityInformation(onPosition: Seq[EvaPerson], maxCapacity: Int) derives ReadWriter
