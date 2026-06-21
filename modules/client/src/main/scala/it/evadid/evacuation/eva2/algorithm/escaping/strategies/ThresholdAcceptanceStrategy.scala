package it.evadid.evacuation.eva2.algorithm.escaping.strategies

import it.evadid.evacuation.core.datastructures.matrix.{Neighbourhood, PositionInMatrix}
import it.evadid.evacuation.eva2.algorithm.escaping.RoutingMap.RoutingMap
import it.evadid.evacuation.eva2.algorithm.escaping.strategies.ThresholdAcceptanceStrategy.{TaSimInfo, TaStepInfo}
import it.evadid.evacuation.eva2.algorithm.escaping.{EvacuationStrategy, PersonOrderSelector, PersonRoutingOption}
import it.evadid.evacuation.eva2.model.{EvaFloorMap, Person}

/**
 * Threshold Accepting Strategy. Always move if the distance is not increased beyond (minimumRemainingDistance * maxDistance)
 * @param personOrdering
 * @param maxThreshold
 */
case class ThresholdAcceptanceStrategy(personOrdering: PersonOrderSelector[TaSimInfo, TaStepInfo], maxThreshold: Double) extends EvacuationStrategy[TaSimInfo, TaStepInfo] {

  override def createInitialSimulationInformation(initialState: EvaFloorMap, neighbourhood: Neighbourhood, routingMap: RoutingMap): TaSimInfo = {
    TaSimInfo(routingMap.onlyPathsWithRelativelength(maxThreshold))
  }

  override def createInitialStepInformation(initialState: EvaFloorMap, neighbourhood: Neighbourhood, stepInformation: TaSimInfo): TaStepInfo = TaStepInfo()

  override def tryToRoute(person: Person, currentState: EvaFloorMap, blockedPositions: collection.Set[PositionInMatrix], neighbourhood: Neighbourhood, simInfo: TaSimInfo, stepInfo: TaStepInfo): Option[PersonRoutingOption[PositionInMatrix]] = {

    val routingOptions = simInfo.routingMap.getRawMap(person.pos)
    val freeRoutingOptions = routingOptions.filter(op => op.nextStep.isDefined && !blockedPositions.contains(op.nextStep.get))
    val closestRoutingOption = freeRoutingOptions.minByOption(_.remainingDistance)
    val chosenOption = closestRoutingOption.map(op => PersonRoutingOption(person, op.nextStep.get, op.destination, op.remainingDistance))
    chosenOption

  }

  override def getPersonOrderSelector: PersonOrderSelector[TaSimInfo, TaStepInfo] = personOrdering
}

object ThresholdAcceptanceStrategy {

  case class TaSimInfo(routingMap: RoutingMap)

  case class TaStepInfo()

}