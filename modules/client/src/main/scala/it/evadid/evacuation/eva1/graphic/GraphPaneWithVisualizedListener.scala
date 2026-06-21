package it.evadid.evacuation.eva1.graphic

import it.evadid.evacuation.eva1.control.traits.evamousevisualizer.MouseListenerVisualizer
import it.evadid.evacuation.eva1.graphic.panes.EvacuationGraphDrawer
import it.evadid.evacuation.eva1.model.ProgramState
import it.evadid.evacuation.shared.traits.graphic.{EvaCanvas, EvaMouseListener}

class GraphPaneWithVisualizedListener[C](canvas: EvaCanvas[C], graphVisualizer: EvacuationGraphDrawer) extends GraphPane[C] {

  def getCanvas: EvaCanvas[C] = canvas

  canvas.addMouseListener(EvaMouseListener.funtionOnActionListener(repaint))


  private var listener: Option[ListenerWithVisualizer[?]] = None


  def repaint(): Unit = {
    val startTime = System.currentTimeMillis()
    canvas.clear()

    graphVisualizer.visualizeGraph(getGraph())
    drawImage(ProgramState.instance.graphicConfig.backgroundImageTransparency.getValue.value)
    listener.foreach(_.visualizeInto(canvas))

    val endTime = System.currentTimeMillis()

    val drawDiff = (endTime - startTime) / 1000.0
    // println("[EVENT] redrawn at " + new Date() + " in " + drawDiff + "s")
  }

  repaint()

  private def setMouseListener[M <: EvaMouseListener](pMouseListener: Option[M], pVisualizer: MouseListenerVisualizer[M]): Unit = setMouseListener(pMouseListener, Some(pVisualizer))

  def setMouseListener[M <: EvaMouseListener](pMouseListener: M, pVisualizer: MouseListenerVisualizer[M]): Unit = setMouseListener(Some(pMouseListener), Some(pVisualizer))


  private def setMouseListener[M <: EvaMouseListener](pMouseListener: Option[M], pVisualizer: Option[MouseListenerVisualizer[M]]): Unit = {
    if (listener.isDefined) {
      canvas.removeMouseListener(listener.get.asBasic)
    }

    if (pMouseListener != null && pMouseListener.isDefined) {
      val newListener =
        if (pVisualizer != null && pVisualizer.isDefined) new ListenerWithVisualizer[M](pMouseListener.get, pVisualizer)
        else new ListenerWithVisualizer[M](pMouseListener.get, pVisualizer)
      listener = Some(newListener)
    } else {
      listener = None
    }

    if (listener.isDefined) {
      canvas.addMouseListener(listener.get.asBasic)
    }

    repaint()
  }

  private case class ListenerWithVisualizer[M <: EvaMouseListener](mouseListener: M, visualizer: Option[MouseListenerVisualizer[M]]) {
    def asBasic: EvaMouseListener = mouseListener

    def visualizeInto(canvas: EvaCanvas[?]): Unit = if (visualizer.isDefined) visualizer.get.visualizeMouseListener(mouseListener, canvas)
  }

}

object GraphPaneWithVisualizedListener {


  def apply[C](canvas: EvaCanvas[C]): GraphPaneWithVisualizedListener[C] = new GraphPaneWithVisualizedListener(canvas, EvacuationGraphDrawer.getStandardDrawer(canvas))

  def apply[C]()(implicit factory: () => EvaCanvas[C]): GraphPaneWithVisualizedListener[C] = {
    val canvas = factory()
    new GraphPaneWithVisualizedListener(canvas, EvacuationGraphDrawer.getStandardDrawer(canvas))
  }


}

