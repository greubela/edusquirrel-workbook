package it.evadid.evacuation.eva1.control

import it.evadid.evacuation.control.EvaControlMode
import it.evadid.evacuation.eva1.graphic.GraphPane
import it.evadid.evacuation.eva1.model.ProgramState
import it.evadid.evacuation.html.elements.EvaWebCanvas
import org.scalajs.dom.html

trait Eva1ControlMode extends EvaControlMode {

  def getMainPane(): GraphPane[html.Canvas]

  implicit def createNewCanvas(): EvaWebCanvas = new EvaWebCanvas(
    ProgramState.instance.graphicConfig.widthDimension.getValue.value * 1.0,
    ProgramState.instance.graphicConfig.heightDimension.getValue.value * 1.0)

}
