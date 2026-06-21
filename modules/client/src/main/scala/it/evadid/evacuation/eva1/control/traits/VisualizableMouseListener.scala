package it.evadid.evacuation.eva1.control.traits

import it.evadid.core.datastructures.graph.Positionable
import it.evadid.evacuation.shared.traits.graphic.EvaMouseListener

trait VisualizableMouseListener[O <: Positionable] extends EvaMouseListener{

  def getState(): GraphObjectSelectorState[O]

}
