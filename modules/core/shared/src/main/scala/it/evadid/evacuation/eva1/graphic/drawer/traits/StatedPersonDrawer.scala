package it.evadid.evacuation.eva1.graphic.drawer.traits

import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.core.graphic.model.EvaColor
import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.algorithm.routing.CapacityInformation
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaEdge
import it.evadid.evacuation.eva1.model.evagraph.{EvaPerson, Router}

import scala.util.Random

trait StatedPersonDrawer {

  def drawPersonsOnNodes(curSimulationTime: Long, curState: MultiHashMapList[Router, EvaPerson], lastEvents: Map[EvaPerson, PersonEvent]): Unit

  def drawPersonOnEdges(curSimulationTime: Long, curState: Map[EvaEdge, CapacityInformation], lastEvents: Map[EvaPerson, PersonEvent]): Unit

  def standardPersonColor(person: EvaPerson): EvaColor = {
    val ran = new Random(person.seed)
    EvaColor(ran.nextInt(255), ran.nextInt(255), ran.nextInt(255), 255)
    //HSBColor(ran.nextDouble(), ran.nextDouble(), ran.nextDouble()*0.25+0.75).toRGB
  }


}
