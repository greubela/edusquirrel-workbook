package it.evadid.evacuation.eva1.graphic.drawer.instances.stated

import it.evadid.evacuation.core.graphic.model.EvaColor
import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.algorithm.routing.CapacityInformation
import it.evadid.evacuation.eva1.graphic.drawer.traits.StatedEdgeDrawer
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaEdge
import it.evadid.evacuation.eva1.model.evagraph.Person
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

case class EdgeUtilizationDrawer(canvas: EvaCanvas[?]) extends StatedEdgeDrawer {

  override def drawEdges(curSimulationTime: Long, edgeInformation: Map[EvaEdge, CapacityInformation], lastEvents: Map[Person, PersonEvent]): Unit = if (edgeInformation.keys.nonEmpty) {
    val edges = edgeInformation.keys
    val maxCapacity = edges.map(_.content.capacityPerSecond).max
    val minCapacity = edges.map(_.content.capacityPerSecond).min

    edges.foreach(edge => {
      val range = (maxCapacity - minCapacity)
      val width = 10.0 * (edge.content.capacityPerSecond - minCapacity) / range

      val percent: Double = edgeInformation(edge).onPosition.size * 1.0 / edgeInformation(edge).maxCapacity

      val utilizationColor = EvaColor.getColorGradient(EvaColor(0, 128, 0), EvaColor(255, 0, 0), percent, false)

      // println("EdgeUtilizationDrawer::drawEdges. edge: " + edge + ", capacity: " + edge.content.capacityPerSecond + ", width: " + width)

      canvas.setStrokeColor(utilizationColor)
      canvas.drawLine(edge.start.pos.x, edge.start.pos.y, edge.dest.pos.x, edge.dest.pos.y, width)
    })
  }


}
