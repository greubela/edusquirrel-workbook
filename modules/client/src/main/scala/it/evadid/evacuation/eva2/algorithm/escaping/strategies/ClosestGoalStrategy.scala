package it.evadid.evacuation.eva2.algorithm.escaping.strategies

import it.evadid.evacuation.core.datastructures.matrix.{Neighbourhood, PositionInMatrix}
import it.evadid.evacuation.eva2.algorithm.escaping.RoutingMap.RoutingMap
import it.evadid.evacuation.eva2.algorithm.escaping.strategies.ClosestGoalStrategy.{CGSimInfo, CGStepInfo}
import it.evadid.evacuation.eva2.algorithm.escaping.{EvacuationStrategy, PersonOrderSelector, PersonRoutingOption}
import it.evadid.evacuation.eva2.model.{EvaFloorMap, Person}

/**
 * Closest Goal Strategy. Always take the tile that minimizes the distance to the closest goal.
 * @param personOrdering
 */
case class ClosestGoalStrategy(personOrdering: PersonOrderSelector[CGSimInfo, CGStepInfo]) extends EvacuationStrategy[CGSimInfo, CGStepInfo] {

  override def createInitialSimulationInformation(initialState: EvaFloorMap, neighbourhood: Neighbourhood, routingMap: RoutingMap): CGSimInfo = {
    CGSimInfo(routingMap.onlyShortestPaths())
  }

  override def createInitialStepInformation(initialState: EvaFloorMap, neighbourhood: Neighbourhood, stepInformation: CGSimInfo): CGStepInfo = CGStepInfo()

  override def tryToRoute(person: Person, currentState: EvaFloorMap, blockedPositions: collection.Set[PositionInMatrix], neighbourhood: Neighbourhood, simInfo: CGSimInfo, stepInfo: CGStepInfo): Option[PersonRoutingOption[PositionInMatrix]] = {

    val routingOptions = simInfo.routingMap.getRawMap(person.pos)
    val freeRoutingOptions = routingOptions.filter(op => op.nextStep.isDefined && !blockedPositions.contains(op.nextStep.get))
    val chosenOption = freeRoutingOptions.headOption.map(op => PersonRoutingOption(person, op.nextStep.get, op.destination, op.remainingDistance))
    chosenOption

  }

  override def getPersonOrderSelector: PersonOrderSelector[CGSimInfo, CGStepInfo] = personOrdering
}

object ClosestGoalStrategy {

  case class CGSimInfo(routingMap: RoutingMap)

  case class CGStepInfo()

}