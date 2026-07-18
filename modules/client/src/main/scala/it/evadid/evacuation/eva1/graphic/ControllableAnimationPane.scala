package it.evadid.evacuation.eva1.graphic

import it.evadid.evacuation.eva1.algorithm.routing.EvacuationFlowSimulation
import it.evadid.evacuation.eva1.graphic.panes.EvacuationAnimationDrawer
import it.evadid.evacuation.eva1.model.ProgramState
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

class ControllableAnimationPane[C](evacuation: EvacuationFlowSimulation, canvas: EvaCanvas[C], visualizer: EvacuationAnimationDrawer) extends GraphPane[C] {

  private var time: Long = 0

  def getCanvas: EvaCanvas[C] = canvas

  def getTime(): Long = time

  def repaint(): Unit = {
    val startTime = System.currentTimeMillis()

    canvas.clear()
    drawImage(ProgramState.instance.graphicConfig.backgroundImageTransparency.getValue.value)
    visualizer.visualizeSituation(time, evacuation)

    val endTime = System.currentTimeMillis()

    val drawDiff = (endTime - startTime) / 1000.0
      //println("redrawn in " + drawDiff + "s")
  }

  def setTime(newTime: Long): Unit = {
    time = newTime
    repaint()
  }

  def incTime(inc: Long, automaticNewStart: Boolean = false): Unit = if (inc != 0) {
    val newTime = time + inc
    val evaDur = evacuation.getStates().last.currenTimestamp
    if (evaDur > 0 && automaticNewStart && newTime > evaDur) {
      time = newTime % evaDur
    } else if (!automaticNewStart && newTime > evaDur) {
      time = evaDur
    } else if (automaticNewStart && newTime < 0) {
      time = evaDur - newTime
    } else if (!automaticNewStart && newTime < 0) {
      time = 0
    } else {
      time = newTime
    }
    repaint()
  }

  repaint()

}

object ControllableAnimationPane {

  def apply[C](animation: EvacuationFlowSimulation, canvas: EvaCanvas[C]): ControllableAnimationPane[C] =
    new ControllableAnimationPane(animation, canvas, EvacuationAnimationDrawer.getStandardDrawer(canvas))

  def apply[C](animation: EvacuationFlowSimulation)(implicit createCanvas: () => EvaCanvas[C]): ControllableAnimationPane[C] = apply(animation, createCanvas())
}