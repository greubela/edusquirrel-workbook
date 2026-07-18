package it.evadid.evacuation.eva1.algorithm.events.eventtypes

import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaGraph
import it.evadid.evacuation.eva1.model.evagraph.{ObservableEvaGraphModel, EvaPerson, Router}

case class PersonInsertedEvent(person: EvaPerson, router: Router, graph: EvaGraph, eventStartTimestamp: Long, val simulationStartedTimestampInMs: Long) extends PersonEvent{
  override def toString: String = {
    "PIE(person = " + person.seed + ", pos: " + router + ", time: " + eventStartTimestamp + ")"
  }

}
