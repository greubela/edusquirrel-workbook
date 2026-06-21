package it.evadid.evacuation.eva2.algorithm.escaping.strategies

import it.evadid.evacuation.core.datastructures.matrix.{Neighbourhood, PositionInMatrix}
import it.evadid.evacuation.eva2.algorithm.escaping.{EvacuationStrategy, PersonOrderSelector, PersonRoutingOption}
import it.evadid.evacuation.eva2.algorithm.escaping.RoutingMap.RoutingMap
import it.evadid.evacuation.eva2.algorithm.escaping.strategies.MultipleGoalStrategy.{MgSimInfo, MgStepInfo}
import it.evadid.evacuation.eva2.model.{EvaFloorMap, Person}

/**
 * Multiple Goal Strategy: Always take one of the tiles that minimizes the distance to one of the goals. Only goals that are reachable within (minRemainingDistance * maxThreshold) are considered
 * @param personOrdering
 * @param maxThreshold
 */
case class MultipleGoalStrategy(personOrdering: PersonOrderSelector[MgSimInfo, MgStepInfo], maxThreshold: Double) extends EvacuationStrategy[MgSimInfo, MgStepInfo] {

  override def createInitialSimulationInformation(initialState: EvaFloorMap, neighbourhood: Neighbourhood, routingMap: RoutingMap): MgSimInfo = {
    MgSimInfo(routingMap.removeMultipleWaysToSingleGoal().onlyPathsWithRelativelength(maxThreshold))
  }

  override def createInitialStepInformation(initialState: EvaFloorMap, neighbourhood: Neighbourhood, stepInformation: MgSimInfo): MgStepInfo = MgStepInfo()

  override def tryToRoute(person: Person, currentState: EvaFloorMap, blockedPositions: collection.Set[PositionInMatrix], neighbourhood: Neighbourhood, simInfo: MgSimInfo, stepInfo: MgStepInfo): Option[PersonRoutingOption[PositionInMatrix]] = {


    val routingOptions = simInfo.routingMap.getRawMap(person.pos)
    val definedRoutingOptions = routingOptions.filter(op => op.nextStep.isDefined)
    val freeRoutingOptions = definedRoutingOptions.filter(op => !blockedPositions.contains(op.nextStep.get))
    val closestRoutingOption = freeRoutingOptions.minByOption(_.remainingDistance)
    val chosenOption = closestRoutingOption.map(op => PersonRoutingOption(person, op.nextStep.get, op.destination, op.remainingDistance))
    chosenOption

  }

  override def getPersonOrderSelector: PersonOrderSelector[MgSimInfo, MgStepInfo] = personOrdering
}

object MultipleGoalStrategy {

  case class MgSimInfo(routingMap: RoutingMap)

  case class MgStepInfo()

}