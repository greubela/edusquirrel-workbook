package it.evadid.evacuation.eva1.control.traits.evamousevisualizer

import it.evadid.evacuation.shared.traits.graphic.{EvaCanvas, EvaMouseListener}

trait MouseListenerVisualizer[M <: EvaMouseListener] {

  def visualizeMouseListener(mouseListener: M, canvas: EvaCanvas[?]): Unit

}
