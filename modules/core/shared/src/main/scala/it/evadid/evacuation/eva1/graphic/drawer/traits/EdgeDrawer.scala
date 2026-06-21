package it.evadid.evacuation.eva1.graphic.drawer.traits

import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaEdge

trait EdgeDrawer {

  def drawEdges(edges: Seq[EvaEdge]): Unit

  //def drawEdges(curSimulationTime: Long, edges: Seq[EvaEdge], edgeInformation: Map[EvaEdge, CapacityInformation], lastEvents: Map[Person, PersonEvent]): Unit = drawEdges(edges)

}


