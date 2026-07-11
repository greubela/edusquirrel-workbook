package it.evadid.evacuation.eva1.graphic.drawer.traits

import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.model.evagraph.{EvaPerson, Router}

trait StatedNodeDrawer {

  def drawNodes(curSimulationTime: Long, nodes: Seq[Router], curState: MultiHashMapList[Router, EvaPerson], safePersons: MultiHashMapList[Router, EvaPerson], lastEvents: Map[EvaPerson, PersonEvent]): Unit

}
