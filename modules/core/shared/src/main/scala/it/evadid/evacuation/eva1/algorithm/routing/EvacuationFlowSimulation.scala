package it.evadid.evacuation.eva1.algorithm.routing

import it.evadid.evacuation.core.algorithm.routing.RoutingOptionsToDestinationCalculator
import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.core.datastructures.graph.{PositionableEdge, WeightedNeighbourStructure}
import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.core.utility.Counter
import it.evadid.evacuation.eva1.algorithm.events.eventtypes.PersonInsertedEvent
import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
import it.evadid.evacuation.eva1.algorithm.routing.FlowRoutingMap.FlowRoutingMap
import it.evadid.evacuation.eva1.algorithm.strategy.ClosestGoalStrategy
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.EvaGraph
import it.evadid.evacuation.eva1.model.evagraph.ObservableEvaGraphModel.fillToQuickTest
import it.evadid.evacuation.eva1.model.evagraph.{ConnectionInfo, EvaGraphModel, ObservableEvaGraphModel, EvaPerson, Router}

import scala.collection.mutable


class EvacuationFlowSimulation(graph: EvaGraph, flowStrategy: FlowStrategy, states: List[EvacuationState]) {


  def getStates(): List[EvacuationState] = states

  def getStateAt(timeInMs: Long): EvacuationState = {

    var lastState = states(0)
    var remStates = states.tail

    while (lastState.currenTimestamp <= timeInMs && remStates.nonEmpty) {
      val nextState = remStates.head
      remStates = remStates.tail

      if (nextState.currenTimestamp <= timeInMs) {
        lastState = nextState
      }
    }
    lastState
  }

}


object EvacuationFlowSimulation {


  def main(args: Array[String]): Unit = {

    val graph = EvaGraphModel.createSportsHall()

    val res: EvacuationFlowSimulation = simulateEvacuation(graph, ClosestGoalStrategy)
    println("finished with " + res.getStates().size + " states, times: " + res.getStates().map(_.currenTimestamp).distinct)

  }


  def simulateEvacuation(graph: EvaGraph, flowStrategy: FlowStrategy): EvacuationFlowSimulation = {

    val startTime = System.nanoTime()

    val states = mutable.ListBuffer[EvacuationState]()
    var lastState: Option[EvacuationState] = Some(getInitialState(graph))

    while (lastState.isDefined && states.size < 10000) {
      states += lastState.get
      lastState = lastState.get.calculateNextState(flowStrategy)
    }

    val endTime = System.nanoTime()
    val diff = (endTime - startTime) / 1000000 / 100 / 10.0

    System.out.println("Finished evacuation in " + diff + "s")

    new EvacuationFlowSimulation(graph, flowStrategy, states.toList)
  }

  def getInitialState(graph: EvaGraph): EvacuationState = {
    val initialState = PositionStateMap.getEmpty(graph)
    val routingsMap: FlowRoutingMap = getRoutingOptionsMap(graph)

    val insertionEvents = getInitialEvents(graph)
    val state = EvacuationState(initialState, insertionEvents.map(_.person).toSet, routingsMap, 0, List(), insertionEvents.map(_.asInstanceOf[PersonEvent]).toSet)
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

  private def getInitialEvents(graph: EvaGraph): mutable.HashSet[PersonInsertedEvent] = {
    val counter = new Counter()
    val res = new mutable.HashSet[PersonInsertedEvent]()
    graph.nodes.foreach(node => {
      val initPersonsAtNode = node.initCapacity
      1.to(initPersonsAtNode).foreach(personNr => {
        val person = EvaPerson(counter.getNext, List())
        res += PersonInsertedEvent(person, node, graph, 0, -1)
      })
    })
    res
  }


}
