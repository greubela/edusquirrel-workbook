package it.evadid.evacuation.eva1.graphic

import it.evadid.evacuation.eva1.model.ProgramState
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaGraph
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

trait GraphPane[C] {

  def repaint(): Unit

  def getRawCanvas: C = getCanvas.getCanvasElement

  def getCanvas: EvaCanvas[C]

  def getGraph(): EvaGraph = ProgramState.graph()

  def drawImage(alphaUpTo255: Int): Unit = if (ProgramState.instance.graphicConfig.centerBackgroundImage.getValue.value) {
    ???
  } else {
    ProgramState.instance.backgroundImage.currentValue.foreach(img => getCanvas.drawImageWithAlpha(0, 0, alphaUpTo255, img))
  }

}
