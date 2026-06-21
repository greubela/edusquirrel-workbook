package it.evadid.evacuation.eva1.algorithm.events.eventtypes

import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.{EvaEdge, EvaGraph}
import it.evadid.evacuation.eva1.model.evagraph.{ObservableEvaGraphModel, Person}


case class PersonReceivedEvent(person: Person, edge: EvaEdge, graph: EvaGraph, eventStartTimestamp: Long, val simulationStartedTimestampInMs: Long) extends PersonEvent {
  override def toString: String = {
    "PRE(person = " + person.seed + ", pos: " + edge + ", time: " + eventStartTimestamp + ")"
  }

}