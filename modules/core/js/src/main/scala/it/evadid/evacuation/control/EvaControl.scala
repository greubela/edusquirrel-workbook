package it.evadid.evacuation.control

import it.evadid.evacuation.html.HtmlHelper
import org.scalajs.dom.{Element, document}
import scala.concurrent.ExecutionContext

trait EvaControl[T <: EvaControlMode] {


  protected implicit val context: ExecutionContext = ExecutionContext.global

  protected var controlMode: T = getNoopMode()

  def reloadMainArea(): Unit

  def redrawMainArea(): Unit

  def reload(): Unit = {
    println("--- reloading now!")
    val startTime = System.currentTimeMillis()
    reloadMainArea()
    val midTime = System.currentTimeMillis()
    reloadControl()
    val diff1 = (System.currentTimeMillis() - midTime) / 1000.0
    val diff0 = (midTime - startTime) / 1000.0
    println("    finished reloading. Main Area in " + diff0 + "s, Control Area in " + diff1 + "s! ---")
  }

  def reloadControl(): Unit = {
    println("controlMode: " + controlMode.getClass + " (element: " + controlMode.getControlElement + ")")
    HtmlHelper.clearChildrenFromId("eva-control")
    val mainControl: Element = document.getElementById("eva-control")
    mainControl.appendChild(controlMode.getControlElement)
  }

  def setNewControlMode(newControlMode: T): Unit = {
    if (newControlMode == null) {
      setNewControlMode(getNoopMode())
    } else {
      println("setting mode: " + newControlMode.getClass + " (old mode: " + controlMode.getClass + ")")
      controlMode.onLeavingMode()
      controlMode = newControlMode
      controlMode.onEnteringMode()
      reload()
    }
  }

  def getNoopMode(): T

}
