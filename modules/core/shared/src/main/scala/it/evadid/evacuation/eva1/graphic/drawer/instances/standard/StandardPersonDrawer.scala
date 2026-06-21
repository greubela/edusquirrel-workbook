package it.evadid.evacuation.eva1.graphic.drawer.instances.standard

import it.evadid.evacuation.core.datastructures.graphs.Position
import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.algorithm.routing.CapacityInformation
import it.evadid.evacuation.eva1.graphic.drawer.traits.StatedPersonDrawer
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaEdge
import it.evadid.evacuation.eva1.model.evagraph.{Person, Router}
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

class StandardPersonDrawer(evaCanvas: EvaCanvas[_]) extends StatedPersonDrawer {


  override def drawPersonsOnNodes(curSimulationTime: Long, curState: MultiHashMapList[Router, Person], lastEvents: Map[Person, PersonEvent]): Unit = {
  }

  override def drawPersonOnEdges(curSimulationTime: Long, curState: Map[EvaEdge, CapacityInformation], lastEvents: Map[Person, PersonEvent]): Unit = {


    curState.keys.foreach(edge => {
      val persons = curState(edge)

      persons.onPosition.foreach(person => {
        val timeDiff = curSimulationTime - lastEvents(person).eventStartTimestamp
        val percentFinished = (timeDiff * 1.0) / edge.content.delayInMs

        val pos = Position.between(edge.start.pos, edge.dest.pos, percentFinished)
        evaCanvas.setFillColor(standardPersonColor(person))
        evaCanvas.fillCircle(pos.x , pos.y , 6)

      })

    })


  }
}
