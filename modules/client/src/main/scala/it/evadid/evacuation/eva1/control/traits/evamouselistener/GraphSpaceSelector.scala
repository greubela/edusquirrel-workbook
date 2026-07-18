package it.evadid.evacuation.eva1.control.traits.evamouselistener

import it.evadid.evacuation.core.datastructures.graphs.Position
import it.evadid.evacuation.eva1.control.traits.{GraphObjectSelectorState, VisualizableMouseListener}
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaGraph

abstract class GraphSpaceSelector() extends VisualizableMouseListener[Position] {

  private var curMousePos: Option[Position] = None
  private var closePositions: Seq[Position] = List()

  override def onMouseMoved(x: Double, y: Double): Unit = {
    curMousePos = Some(x, y)
    calcClosePositions(x, y)
  }

  override def onMouseEntered(x: Double, y: Double): Unit = {
    curMousePos = Some(x, y)
  }

  override def onMouseExited(x: Double, y: Double): Unit = {
    curMousePos = None
  }

  override def onMouseClicked(x: Double, y: Double, primaryButton: Boolean): Unit = if (closePositions.isEmpty) {
    onSpaceSelected(x, y, primaryButton)
  }

  private def calcClosePositions(x: Double, y: Double): Unit = {
    val mousePos: Position = Position(x, y)
    val closeNodes = if (minDistToNodes().isDefined) graph.nodes.filter(node => node.pos.distTo(mousePos) < minDistToNodes().get) else List()
    val closeEdges = if (minDistToEdges().isDefined) graph.edges.filter(edge => EvaGraphTypes.posOfEdge(edge).distTo(mousePos) < minDistToEdges().get) else List()

    closePositions = closeNodes.map(_.pos) ++ closeEdges.map(EvaGraphTypes.posOfEdge)
  }

  def minDistToEdges(): Option[Integer]

  def minDistToNodes(): Option[Integer]

  def onSpaceSelected(x: Double, y: Double, primaryButton: Boolean): Unit

  def graph: EvaGraph

  override def getState(): GraphObjectSelectorState[Position] = GraphObjectSelectorState[Position](curMousePos, closePositions, List())
}
