package it.evadid.evacuation.eva1.graphic.drawer.instances.stated

import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.core.graphic.model.EvaColor
import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.graphic.drawer.traits.{NodeDrawer, StatedNodeDrawer}
import it.evadid.evacuation.eva1.graphic.panes.EvacuationAnimationDrawer
import it.evadid.evacuation.eva1.model.evagraph.{EvaPerson, Router}
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

class LabeledNodeDrawer(canvas: EvaCanvas[?]) extends StatedNodeDrawer {


  override def drawNodes(curSimulationTime: Long, nodes: Seq[Router], curState: MultiHashMapList[Router, EvaPerson], safePersons: MultiHashMapList[Router, EvaPerson], lastEvents: Map[EvaPerson, PersonEvent]): Unit = {

    val font = EvacuationAnimationDrawer.defaultFont
    canvas.setFont(font)

    nodes.foreach(node => {

      canvas.setColor(EvaColor(0, 0, 255))
      canvas.fillCircle(node.pos.x , node.pos.y , 10)

      if (curState(node).nonEmpty)
        NodeDrawer.drawLabel(canvas, node, EvaColor(235, 235, 235), EvaColor(0, 0, 0), EvaColor(0, 0, 0), font, curState(node).size.toString)

      if(safePersons(node).nonEmpty)
        NodeDrawer.drawLabel(canvas, node, EvaColor(0, 128, 0), EvaColor(0, 0, 0), EvaColor(255, 255, 255), font, safePersons(node).size.toString)

    })

  }

}