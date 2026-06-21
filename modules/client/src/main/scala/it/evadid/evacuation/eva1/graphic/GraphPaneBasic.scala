package it.evadid.evacuation.eva1.graphic


import it.evadid.evacuation.eva1.graphic.panes.EvacuationGraphDrawer
import it.evadid.evacuation.eva1.model.ProgramState
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

class GraphPaneBasic[C](canvas: EvaCanvas[C], graphVisualizer: EvacuationGraphDrawer) extends GraphPane[C] {

  override def getRawCanvas: C =
    canvas.getCanvasElement

  def getCanvas: EvaCanvas[C] = canvas

  def repaint(): Unit = {
    val startTime = System.currentTimeMillis()
    canvas.clear()
    drawImage(ProgramState.instance.graphicConfig.backgroundImageTransparency.getValue.value)
    graphVisualizer.visualizeGraph(getGraph())
    val endTime = System.currentTimeMillis()

    val drawDiff = (endTime - startTime) / 1000.0
    // println("[EVENT] redrawn at " + new Date() + " in " + drawDiff + "s")
  }

  repaint()

  override def toString: String = "GraphicPaneBasic(" + hashCode() + ", " + getCanvas + ")"

}

object GraphPaneBasic {

  def apply[C](canvas: EvaCanvas[C]): GraphPaneBasic[C] = new GraphPaneBasic(canvas, EvacuationGraphDrawer.getStandardDrawer(canvas))
  def apply[C]()(implicit createCanvas: () => EvaCanvas[C]): GraphPaneBasic[C] = apply(createCanvas())

}
