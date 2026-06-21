package it.evadid.evacuation.eva2.algorithm.escaping

import it.evadid.evacuation.eva2.model.Person

 case class PersonRoutingOption[N](person: Person, nextStep: N, destination: N, remainingDistance: Double) {
  override def toString: String = s"${person.id}: $nextStep->$destination in $remainingDistance"
}
