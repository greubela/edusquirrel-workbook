package it.evadid.evacuation.eva2.algorithm.escaping

import it.evadid.core.datastructures.matrix.{Neighbourhood, PositionInMatrix}
import it.evadid.evacuation.eva2.algorithm.escaping.RoutingMap.RoutingMap
import it.evadid.evacuation.eva2.model.{EvaFloorMap, Person}


trait EvacuationStrategy[I, J] {

  def createInitialSimulationInformation(initialState: EvaFloorMap, neighbourhood: Neighbourhood, routingMap: RoutingMap): I

  def createInitialStepInformation(initialState: EvaFloorMap, neighbourhood: Neighbourhood, stepInformation: I): J

  def calculateNextMicroStep(currentState: EvaFloorMap, neighbourhood: Neighbourhood, blockedPositions: collection.Set[PositionInMatrix], simInfo: I, stepInfo: J, alreadyMoved: collection.Set[Person], notMovedYet: collection.Set[Person]): Option[PersonRoutingOption[PositionInMatrix]] = {

    var notMovedOrdered = getPersonOrderSelector.setOrderOfMicroMovement(currentState, notMovedYet.toSeq)
    var desiredMovement: Option[PersonRoutingOption[PositionInMatrix]] = None
    while (desiredMovement.isEmpty && notMovedOrdered.nonEmpty) {
      val nextToTry = notMovedOrdered.head
      notMovedOrdered = notMovedOrdered.tail

      desiredMovement = tryToRoute(nextToTry, currentState, blockedPositions, neighbourhood, simInfo, stepInfo)
    }
    desiredMovement
  }

  def tryToRoute(person: Person, currentState: EvaFloorMap, blockedPositions: collection.Set[PositionInMatrix], neighbourhood: Neighbourhood, simInfo: I, stepInfo: J): Option[PersonRoutingOption[PositionInMatrix]]

  def getPersonOrderSelector: PersonOrderSelector[I, J]

}

