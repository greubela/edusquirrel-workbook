package it.evadid.evacuation.eva1.graphic.drawer.instances.standard

import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.core.graphic.model.EvaColor
import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.graphic.drawer.traits.{NodeDrawer, StatedNodeDrawer}
import it.evadid.evacuation.eva1.graphic.panes.EvacuationAnimationDrawer
import it.evadid.evacuation.eva1.model.evagraph.{Person, Router}
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

class InitPersonsNodeDrawer(canvas: EvaCanvas[?]) extends NodeDrawer with StatedNodeDrawer {

  override def drawNodes(nodes: Seq[Router]): Unit = {
    nodes.foreach(node => {

      if (node.isExit) {
        canvas.setColor(EvaColor(0, 128, 0))
        canvas.fillCircle(node.pos.x, node.pos.y, 16)
        canvas.setColor(EvaColor(0, 255, 0))
        canvas.drawCircle(node.pos.x, node.pos.y, 16)
      } else {
        canvas.setFillColor(EvaColor(0, 0, 0))
        canvas.fillCircle(node.pos.x, node.pos.y, 10)
      }

      if (node.initCapacity > 0) {
        NodeDrawer.drawLabel(canvas, node, EvaColor(255, 200, 200), EvaColor(255, 0, 0), EvaColor(0, 0, 0), EvacuationAnimationDrawer.defaultFont, node.initCapacity.toString)
      }

    })
  }

  override def drawNodes(curSimulationTime: Long, nodes: Seq[Router], curState: MultiHashMapList[Router, Person], safePersons: MultiHashMapList[Router, Person], lastEvents: Map[Person, PersonEvent]): Unit = {
    drawNodes(nodes)
  }
}
