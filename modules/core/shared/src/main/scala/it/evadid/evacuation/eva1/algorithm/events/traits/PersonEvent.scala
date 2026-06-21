package it.evadid.evacuation.eva1.algorithm.events.traits

import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaGraph
import it.evadid.evacuation.eva1.model.evagraph.{ObservableEvaGraphModel, Person}

trait PersonEvent extends Event {
  def person: Person
  def eventStartTimestamp: Long
  def simulationStartedTimestampInMs: Long
  def graph: EvaGraph
}



