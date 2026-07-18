package it.evadid.evacuation.eva1.graphic.panes

import it.evadid.evacuation.core.graphic.model.EvaFont
import it.evadid.evacuation.eva1.algorithm.routing.EvacuationFlowSimulation
import it.evadid.evacuation.eva1.graphic.drawer.instances.standard.StandardPersonDrawer
import it.evadid.evacuation.eva1.graphic.drawer.instances.stated.{EdgeUtilizationDrawer, LabeledNodeDrawer}
import it.evadid.evacuation.eva1.graphic.drawer.traits.{StatedEdgeDrawer, StatedNodeDrawer, StatedPersonDrawer}
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

class EvacuationAnimationDrawer(canvas: EvaCanvas[?], personDrawer: StatedPersonDrawer, edgeDrawer: StatedEdgeDrawer, nodeDrawer: StatedNodeDrawer) {

  def visualizeSituation(currentTime: Long, simulation: EvacuationFlowSimulation): Unit = {
    val evacuationState = simulation.getStateAt(currentTime)
    val graph = evacuationState.curPositionsInState.graph

    edgeDrawer.drawEdges(currentTime, evacuationState.curPositionsInState.capacityMap(), evacuationState.lastEventMap())
    nodeDrawer.drawNodes(currentTime, graph.nodes, evacuationState.curPositionsInState.routerMap(), evacuationState.getSafePersons, evacuationState.lastEventMap())
    personDrawer.drawPersonOnEdges(currentTime, evacuationState.curPositionsInState.capacityMapDirected(), evacuationState.lastEventMap())
    personDrawer.drawPersonsOnNodes(currentTime, evacuationState.curPositionsInState.routerMap(), evacuationState.lastEventMap())
  }
}

object EvacuationAnimationDrawer {

  def getStandardDrawer(canvas: EvaCanvas[?]): EvacuationAnimationDrawer =
    new EvacuationAnimationDrawer(canvas, new StandardPersonDrawer(canvas), new EdgeUtilizationDrawer(canvas), new LabeledNodeDrawer(canvas))


  val defaultFont: EvaFont = EvaFont(12, "Arial")

}
