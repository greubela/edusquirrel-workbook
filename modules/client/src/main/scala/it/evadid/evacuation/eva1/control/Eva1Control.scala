package it.evadid.evacuation.eva1.control

import it.evadid.evacuation.control.EvaControl
import it.evadid.evacuation.eva1.control.modes.NoopMode
import it.evadid.evacuation.eva1.graphic.GraphPaneBasic
import it.evadid.evacuation.html.HtmlHelper
import it.evadid.evacuation.html.elements.EvaWebCanvas
import org.scalajs.dom.{Element, document}

object Eva1Control extends EvaControl[Eva1ControlMode] {

  override def reloadMainArea(): Unit = {
    HtmlHelper.clearChildrenFromId("eva-main")
    document.getElementById("eva-main").appendChild(controlMode.getMainPane().getRawCanvas)
    controlMode.getMainPane().repaint()
  }

  override def redrawMainArea(): Unit = {
    controlMode.getMainPane().repaint()
  }

  override def getNoopMode(): Eva1ControlMode = new NoopMode()

}
