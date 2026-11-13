package it.evadid.evacuation.eva1.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.RoutingOptionsToDestinationCalculator
import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.core.datastructures.graph.{PositionableEdge, WeightedNeighbourStructure}
import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.eva1.algorithm.events.eventtypes.PersonInsertedEvent
import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.algorithm.routing.FlowRoutingMap.FlowRoutingMap
import it.evadid.evacuation.eva1.algorithm.strategy.ClosestGoalStrategy
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaGraph
import it.evadid.evacuation.eva1.model.evagraph.{ConnectionInfo, EvaGraphModel, EvaPerson, Router}



class EvacuationFlowSimulation(states: List[EvacuationState]) {
  require(states.nonEmpty, "An evacuation simulation must contain its initial state")
  require(states.zip(states.drop(1)).forall((before, after) => before.currenTimestamp <= after.currenTimestamp),
    "Evacuation states must be ordered by timestamp")

  def getStates(): List[EvacuationState] = states

  def getStateAt(timeInMs: Long): EvacuationState =
    states.tail.iterator.takeWhile(_.currenTimestamp <= timeInMs).foldLeft(states.head)((_, state) => state)
}


object EvacuationFlowSimulation {


  def main(args: Array[String]): Unit = {

    val graph = EvaGraphModel.createSportsHall()

    val res: EvacuationFlowSimulation = simulateEvacuation(graph, ClosestGoalStrategy)
    println("finished with " + res.getStates().size + " states, times: " + res.getStates().map(_.currenTimestamp).distinct)

  }


  def simulateEvacuation(graph: EvaGraph, flowStrategy: FlowStrategy): EvacuationFlowSimulation = {

    val startTime = System.nanoTime()

    val states = collectStates(getInitialState(graph), flowStrategy)

    val endTime = System.nanoTime()
    val diff = (endTime - startTime) / 1000000 / 100 / 10.0

    System.out.println("Finished evacuation in " + diff + "s")

    new EvacuationFlowSimulation(states)
  }

  // Keep the existing bound, including its final recorded state, without computing an unused successor.
  private val MaxStates = 10000

  private def collectStates(initial: EvacuationState, strategy: FlowStrategy): List[EvacuationState] = {
    @scala.annotation.tailrec
    def collect(current: EvacuationState, remaining: Int, previous: List[EvacuationState]): List[EvacuationState] =
      if remaining == 1 then (current :: previous).reverse
      else current.calculateNextState(strategy) match {
        case Some(next) => collect(next, remaining - 1, current :: previous)
        case None => (current :: previous).reverse
      }
    collect(initial, MaxStates, Nil)
  }

  def getInitialState(graph: EvaGraph): EvacuationState = {
    val initialState = PositionStateMap.getEmpty(graph)
    val routingsMap: FlowRoutingMap = getRoutingOptionsMap(graph)

    val insertionEvents = getInitialEvents(graph)
    val state = EvacuationState(initialState, insertionEvents.map(_.person).toSet, routingsMap, 0, List(), insertionEvents.map(event => event: PersonEvent))
    state
  }


  private def getRoutingOptionsMap(graph: EvaGraph): MultiHashMapList[Router, RoutingOption[Router]] = {

    val resultMap: MultiHashMapList[Router, RoutingOption[Router]] = new MultiHashMapList[Router, RoutingOption[Router]]()
    val calculator = new RoutingOptionsToDestinationCalculator()

    val savePositions = graph.nodes.filter(_.isExit)
    println("nodes: " + graph.nodes.size + ", edges: " + graph.edges.size + ", save positions: " + savePositions)


    val wgs: WeightedNeighbourStructure[Router, ConnectionInfo, PositionableEdge[Router, ConnectionInfo]] = graph

    savePositions.map(obj => calculator.routingOptionsMap(wgs, obj)).foreach(resultMap.addAll)

    resultMap
  }

  private def getInitialEvents(graph: EvaGraph): Set[PersonInsertedEvent] =
    graph.nodes.toList.flatMap(node => List.fill(node.initCapacity)(node)).zipWithIndex.map { (node, index) =>
      PersonInsertedEvent(EvaPerson(index, Nil), node, graph, 0, -1)
    }.toSet

}
