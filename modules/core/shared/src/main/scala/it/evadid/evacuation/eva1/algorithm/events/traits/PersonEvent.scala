package it.evadid.evacuation.eva1.algorithm.events.traits

import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaGraph
import it.evadid.evacuation.eva1.model.evagraph.{ObservableEvaGraphModel, EvaPerson}

trait PersonEvent extends Event {
  def person: EvaPerson
  def eventStartTimestamp: Long
  def simulationStartedTimestampInMs: Long
  def graph: EvaGraph
}



