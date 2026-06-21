package it.evadid.evacuation.eva1.graphic.panes


import it.evadid.evacuation.eva1.graphic.drawer.instances.standard.{InitPersonsNodeDrawer, StandardEdgeDrawer}
import it.evadid.evacuation.eva1.graphic.drawer.traits.{EdgeDrawer, NodeDrawer}
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaGraph
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

class EvacuationGraphDrawer(canvas: EvaCanvas[_], nodeDrawer: NodeDrawer, edgeDrawer: EdgeDrawer) {

  def visualizeGraph(graph: EvaGraph): Unit = {
    edgeDrawer.drawEdges(graph.edges)
    nodeDrawer.drawNodes(graph.nodes)
  }
}

object EvacuationGraphDrawer {

  def getStandardDrawer(canvas: EvaCanvas[_]): EvacuationGraphDrawer =
    new EvacuationGraphDrawer(canvas, new InitPersonsNodeDrawer(canvas), new StandardEdgeDrawer(canvas))

}
