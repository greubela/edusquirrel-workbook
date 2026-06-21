package it.evadid.evacuation.eva1.algorithm.events.traits

import it.evadid.evacuation.eva1.algorithm.routing.EvacuationState

trait SimulationStateVisualizer {

  def visualizeSituation(currentTime: Long, evacuationState: EvacuationState): Unit

}
