package it.evadid.evacuation.control

import org.scalajs.dom.Element

trait EvaControlMode {

  def onEnteringMode(): Unit

  def onLeavingMode(): Unit

  def getControlElement: Element


}
