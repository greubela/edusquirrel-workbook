package it.evadid.evacuation.eva1.graphic.drawer.instances.standard

import it.evadid.evacuation.core.graphic.model.EvaColor
import it.evadid.evacuation.eva1.graphic.drawer.traits.EdgeDrawer
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaEdge
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

class StandardEdgeDrawer(canvas: EvaCanvas[?]) extends EdgeDrawer {

  override def drawEdges(edges: Seq[EvaEdge]): Unit = if (edges.nonEmpty) {


    val maxCapacity = edges.map(_.content.capacityPerSecond).max
    val minCapacity = edges.map(_.content.capacityPerSecond).min

    edges.foreach(edge => {
      val width = 10.0 * (edge.content.capacityPerSecond - minCapacity) / (maxCapacity - minCapacity)
      val standardColor = EvaColor(128, 128, 128)
      canvas.setStrokeColor(standardColor)
      canvas.setFillColor(standardColor)
      canvas.drawLine(edge.start.pos.x, edge.start.pos.y, edge.dest.pos.x, edge.dest.pos.y, width)
    })

  }

}
