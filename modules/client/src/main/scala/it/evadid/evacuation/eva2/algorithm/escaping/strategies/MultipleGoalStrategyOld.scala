package it.evadid.evacuation.eva2.algorithm.escaping.strategies

import it.evadid.evacuation.core.algorithm.routing.Dijkstra.DijkstraInformation
import it.evadid.evacuation.core.algorithm.routing.{Dijkstra, ReveresedCachedPathfinding}
import it.evadid.core.datastructures.matrix.{Neighbourhood, PositionInMatrix}
import it.evadid.evacuation.eva2.algorithm.escaping.RoutingMap.RoutingMap
import it.evadid.evacuation.eva2.algorithm.escaping.strategies.MultipleGoalStrategyOld.{MGSimulationInformation, MGStepInformation}
import it.evadid.evacuation.eva2.algorithm.escaping.{EvacuationStrategy, PersonOrderSelector, PersonRoutingOption}
import it.evadid.evacuation.eva2.model.FloorMatrix.FloorMatrix
import it.evadid.evacuation.eva2.model.{EvaFloorMap, Person}

import scala.collection.mutable.ListBuffer


case class MultipleGoalStrategyOld(personOrdering: PersonOrderSelector[MGSimulationInformation, MGStepInformation], maxThreshold: Double) extends EvacuationStrategy[MGSimulationInformation, MGStepInformation] {

  override def createInitialSimulationInformation(initialState: EvaFloorMap, neighbourhood: Neighbourhood, routingMap: RoutingMap): MGSimulationInformation = {
    val floor = new FloorMatrix(initialState.floorMatrix)
    val pathfinding = new ReveresedCachedPathfinding(floor.dijkstraAccess(neighbourhood.function), new Dijkstra[PositionInMatrix](), true)
    MGSimulationInformation(FloorMatrix(initialState.floorMatrix), pathfinding)
  }

  override def createInitialStepInformation(initialState: EvaFloorMap, neighbourhood: Neighbourhood, stepInformation: MGSimulationInformation): MGStepInformation = {
    MGStepInformation()
  }

  override def tryToRoute(person: Person, currentState: EvaFloorMap, blockedPositions: collection.Set[PositionInMatrix], neighbourhood: Neighbourhood, simInfo: MGSimulationInformation, stepInfo: MGStepInformation): Option[PersonRoutingOption[PositionInMatrix]] = {

    def calcIndirectRoutingOptions(firstStep: PositionInMatrix): List[PersonRoutingOption[PositionInMatrix]] = {
      // Indirect routing options are necessary: suppose two exits, but one is directly blocked but available via a short trip around the blocking element
      simInfo.floor.savePositions.map(sPos => {
        val path = simInfo.pathfinding.shortestPath(firstStep, sPos)
        PersonRoutingOption(person, firstStep, sPos, path.size)
      }).toList
    }

    def calcDirectRoutingOptions(fromPos: PositionInMatrix): List[PersonRoutingOption[PositionInMatrix]] = {
      simInfo.floor.savePositions.flatMap(sPos => {
        val path = simInfo.pathfinding.shortestPath(fromPos, sPos)
        if (path.size < 2) None
        else Some(PersonRoutingOption(person, path(1), sPos, path.size - 1))
      }).toList
    }

    val unblocked = currentState.calcMovementOptions(person.pos, neighbourhood.function)

    val routings = calcDirectRoutingOptions(person.pos)

    val bestRoutingOptions = ListBuffer[PersonRoutingOption[PositionInMatrix]]()

    if (routings.nonEmpty) {
      val minDist = routings.minBy(_.remainingDistance).remainingDistance
      val (bestOption, influencedByBlocking) = calculateBestOptionFrom(routings, unblocked, minDist)
      bestOption.foreach(bestRoutingOptions += _)

      if (influencedByBlocking) {
        val indirect = unblocked.flatMap(pos => calcIndirectRoutingOptions(pos)).toList
        if (indirect.nonEmpty) {
          val (bestOptionI, influencedByBlockingI) = calculateBestOptionFrom(indirect, unblocked, minDist)
          bestOptionI.foreach(bestRoutingOptions += _)
        }
      } else {
      }
    }

    bestRoutingOptions.filterInPlace(option => {
      val curDistToChosenSavePoint = simInfo.pathfinding.shortestPath(person.pos, option.destination).size
      // println("option: " + option + " curDistToChosenSavePoint: " + curDistToChosenSavePoint)
      option.remainingDistance < curDistToChosenSavePoint
    })


    val res = bestRoutingOptions.toList.minByOption(_.remainingDistance)

    res

  }

  private def calculateBestOptionFrom[N](routingOptions: List[PersonRoutingOption[N]], unblocked: Set[N], minDist: Double): (Option[PersonRoutingOption[N]], Boolean) = {
    assert(routingOptions.nonEmpty, "Routings must not be empty!")

    val acceptableDistance = routingOptions.filter(_.remainingDistance < minDist * maxThreshold)
    val chosenWithoutBlocking = acceptableDistance.minByOption(_.remainingDistance)
    val unblockedRoutings = acceptableDistance.filter(routing => unblocked.contains(routing.nextStep))
    val chosenPath = unblockedRoutings.minByOption(_.remainingDistance)

    val resultInfluencedByBlocking = chosenPath != chosenWithoutBlocking
    (chosenPath, resultInfluencedByBlocking)

  }

  override def getPersonOrderSelector: PersonOrderSelector[MGSimulationInformation, MGStepInformation] = personOrdering
}

object MultipleGoalStrategyOld {


  case class MGSimulationInformation(floor: FloorMatrix, pathfinding: ReveresedCachedPathfinding[PositionInMatrix, DijkstraInformation])

  case class MGStepInformation()


  //  val notMovedSinceOrdering: Ordering[Person] = Ordering.by(person => notMovedSinceSteps(person.id))

  /*val distanceOrdering: Ordering[Person] = Ordering.by(
    person => cache.floor.savePositions.map(sPos => cache.pathfinding.shortestPath(person.pos, sPos)).map(_.size).min
  )*/


  //val airlineOrdering: Ordering[Person] = Ordering.by(person => cache.floor.savePositions.map(sPos => sPos.cPos.euclidianDistTo(person.pos.cPos)).min)


}
