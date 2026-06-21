package it.evadid.evacuation.eva1.control.traits.evamousevisualizer

import it.evadid.core.datastructures.graph.{Positionable}
import it.evadid.evacuation.core.datastructures.graphs.{Position}
import it.evadid.evacuation.eva1.control.traits.evamouselistener.GraphObjectSelector
import it.evadid.evacuation.shared.traits.graphic.EvaCanvas

trait GraphObjectSelectorVisualizer[O <: Positionable] extends MouseListenerVisualizer[GraphObjectSelector[O]] {

  override def visualizeMouseListener(mouseListener: GraphObjectSelector[O], canvas: EvaCanvas[_]): Unit = {
    val selectorState = mouseListener.getState()
    // println(new Date() + ": visualize selector state!")
    canvas.setColor(GenericGraphSelectorVisualizer.yellowHighlightColor)
    if (selectorState.curMousePos.isDefined) {
      selectorState.curHighlightDest.foreach(
        highlightDest => drawHighlightLine(canvas, Position(selectorState.curMousePos.get.x, selectorState.curMousePos.get.y), Position(highlightDest.pos.x, highlightDest.pos.y))
      )
    }

    selectorState.curSelected.foreach(obj => highlight(canvas, obj))
  }

  def drawHighlightLine(canvas: EvaCanvas[_], start: Position, end: Position): Unit

  def highlight(canvas: EvaCanvas[_], obj: O): Unit

}
