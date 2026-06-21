package it.evadid.evacuation.eva1.control.modes

import it.evadid.evacuation.eva1.control.BasicPaneControlMode
import org.scalajs.dom.{Element, document}

class NoopMode extends BasicPaneControlMode {


  override def onEnteringMode(): Unit = {

  }

  override def onLeavingMode(): Unit = {

  }

  override def getControlElement: Element = document.createElement("div")
}
