package it.evadid.evacuation.eva1.graphic.drawer.traits

import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.algorithm.routing.CapacityInformation
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaEdge
import it.evadid.evacuation.eva1.model.evagraph.Person

trait StatedEdgeDrawer {

  def drawEdges(curSimulationTime: Long, edgeInformation: Map[EvaEdge, CapacityInformation], lastEvents: Map[Person, PersonEvent]): Unit

}


