package it.evadid.evacuation.eva2.algorithm.escaping


import it.evadid.evacuation.core.algorithm.routing.RoutingOptionsToDestinationCalculator
import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.core.datastructures.matrix.{Neighbourhood, PositionInMatrix}
import it.evadid.evacuation.eva2.algorithm.escaping.RoutingMap.RoutingMap
import it.evadid.evacuation.eva2.model.FloorMatrix.FloorMatrix
import it.evadid.evacuation.eva2.model.{EvaFloorMap, Person, ProgramState}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


case class EvacuationStep(microSteps: Seq[EvaFloorMap]) {

  override def toString: String = "Evacuation Step with " + microSteps.size + " micro steps"

}

object EvacuationStep {

  def calculateEvacuationAndMetadata[I, J](): (Evacuation, EvacuationMetaData) = {

    val initialState = ProgramState.instance.floorMap.currentValue

    val timeStart: Long = System.nanoTime()
    val eva = calculateEvacuation(initialState, ProgramState.config.neighbourhood.getValue.value, ProgramState.config.strategy.getValue.value)
    val timeEnd: Long = System.nanoTime()

    val diffInMs = (timeEnd - timeStart) / 1000000

    val meta = EvacuationMetaData(eva, diffInMs, ProgramState.config.neighbourhood.getValue.toPrint, ProgramState.config.strategy.getValue.toPrint)
    (eva, meta)
  }

  def calculateEvacuation[I, J](initialState: EvaFloorMap, neighbour: Neighbourhood, evacuationStrategy: EvacuationStrategy[I, J]): Evacuation = {

    if (new FloorMatrix(initialState.floorMatrix).savePositions.isEmpty) {
      Evacuation(initialState, List(initialState), List(0))
    } else {

      var stepList: ListBuffer[Int] = ListBuffer()
      var stateList: ListBuffer[EvaFloorMap] = ListBuffer()
      var before: EvaFloorMap = null

      def addToBuffer(state: EvaFloorMap): Unit = if (state != before) {
        if (before != state) {
          before = state
          stateList += state
        }
      }

      val time1 = System.nanoTime()
      val routingMap = calculateRoutingMap(initialState, neighbour)
      val time2 = System.nanoTime()
      val evaSteps: Seq[EvacuationStep] = EvacuationStep.calculateEvacuationSteps(initialState,
        evacuationStrategy,
        neighbour,
        evacuationStrategy.createInitialSimulationInformation(initialState, neighbour, routingMap))
      val time3 = System.nanoTime()

      val diff1 = (time2 - time1) / 1000000000.0
      val diff2 = (time3 - time2) / 1000000000.0

      println("routing map: " + diff1 + ", steps: " + diff2)

      evaSteps.foreach(curStep => {
        curStep.microSteps.foreach(addToBuffer)
        stepList += stateList.size - 1
      })

      if(stateList.nonEmpty){
        addToBuffer(stateList.last.removePersonAtSavePoints())
        stepList += (stateList.size - 1)
      }
      println("steps: " + stepList + ", states: " + stateList.size)
      (stateList.toList, stepList.toList)

      Evacuation(initialState, stateList.toList, stepList.toList)
    }
  }

  private def calculateRoutingMap(initialState: EvaFloorMap, neighbourhood: Neighbourhood): RoutingMap = {

    val calculator = new RoutingOptionsToDestinationCalculator()
    val floorMatrix = new FloorMatrix(initialState.floorMatrix)

    val graph = floorMatrix.asGraph(neighbourhood)

    val resultMap = new MultiHashMapList[PositionInMatrix, RoutingOption[PositionInMatrix]]

    floorMatrix.savePositions.map(calculator.routingOptionsMap(graph, _)).foreach(resultMap.addAll)


    resultMap
  }

  private def calculateEvacuationSteps[I, J](initialState: EvaFloorMap, evacuationStrategy: EvacuationStrategy[I, J], neighbourhood: Neighbourhood, evacuationInfo: I): Seq[EvacuationStep] = {
    val evaSteps: mutable.ListBuffer[EvacuationStep] = mutable.ListBuffer()

    var curStep: Option[EvacuationStep] = calcNextStep(initialState, evacuationStrategy, neighbourhood, evacuationInfo)
    while (curStep.nonEmpty) {
      evaSteps += curStep.get
      curStep = calcNextStep(curStep.get.microSteps.last, evacuationStrategy, neighbourhood, evacuationInfo)
    }
    evaSteps.toList
  }

  private def calcNextStep[I, J](initialState: EvaFloorMap, evacuationStrategy: EvacuationStrategy[I, J], neighbourhood: Neighbourhood, evacuationInfo: I): Option[EvacuationStep] = {

    val stepInformation = evacuationStrategy.createInitialStepInformation(initialState, neighbourhood, evacuationInfo)
    val alreadyMoved = new mutable.HashSet[Person]()

    val microStates = new ListBuffer[EvaFloorMap]()

    var curState: Option[EvaFloorMap] = Some(initialState.removePersonAtSavePoints())
    val blockedPositions = mutable.HashSet[PositionInMatrix]() ++ curState.get.persons.map(_.pos)

    val personsToMoveInStep: mutable.HashSet[Person] = mutable.HashSet()
    personsToMoveInStep.addAll(curState.get.persons)

    while (curState.nonEmpty) {
      microStates += curState.get
      val nextMovement = evacuationStrategy.calculateNextMicroStep(curState.get, neighbourhood, blockedPositions, evacuationInfo, stepInformation, alreadyMoved, personsToMoveInStep)

      if (nextMovement.nonEmpty) {
        alreadyMoved += nextMovement.get.person
        personsToMoveInStep -= nextMovement.get.person

        blockedPositions -= nextMovement.get.person.pos
        blockedPositions += nextMovement.get.nextStep

        curState = Some(curState.get.move(nextMovement.get.person, nextMovement.get.nextStep))
      } else {
        curState = None
      }

    }

    if (microStates.size > 1) {
      Some(EvacuationStep(microStates.toList))
    } else {
      None
    }

  }


}
