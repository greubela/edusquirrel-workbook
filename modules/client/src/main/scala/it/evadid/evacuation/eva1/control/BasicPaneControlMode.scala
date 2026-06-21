package it.evadid.evacuation.eva1.control

import it.evadid.evacuation.eva1.graphic.{GraphPane, GraphPaneBasic}
import org.scalajs.dom.html.Canvas

trait BasicPaneControlMode extends Eva1ControlMode {

  private val pane = GraphPaneBasic()(createNewCanvas)

  override def getMainPane(): GraphPane[Canvas] = {
    println("BasicPaneControlMode::getMainPane")
    pane
  }

}
