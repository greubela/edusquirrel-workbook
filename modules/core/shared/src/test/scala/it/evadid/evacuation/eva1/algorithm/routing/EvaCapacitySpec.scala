package it.evadid.evacuation.eva1.algorithm.routing

import it.evadid.core.datastructures.graph.PositionableEdge
import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.evacuation.core.datastructures.graphs.Position
import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
import it.evadid.evacuation.eva1.model.evagraph.*
import it.evadid.evacuation.eva1.model.evagraph.EvaGraphTypes.*
import it.evadid.evacuation.eva1.algorithm.events.eventtypes.*
import it.evadid.evacuation.eva1.algorithm.strategy.*
import munit.FunSuite

class EvaCapacitySpec extends FunSuite {
  private val a = Router(Position(0, 0))
  private val b = Router(Position(10, 0))
  private val exit = Router(Position(20, 0), isExit = true)
  private val forward = new PositionableEdge(a, b, ConnectionInfo(1, 100))
  private val backward = new PositionableEdge(b, a, ConnectionInfo(1, 100))
  private val graph = EvaGraphModel(List(a, b), List(forward, backward))
  private val person = EvaPerson(1, Seq(exit))
  private def routes(options: RoutingOption[Router]*): FlowRoutingMap.FlowRoutingMap = {
    val map = new MultiHashMapList[Router, RoutingOption[Router]]
    options.foreach(o => map.addElement(o.curPos, o))
    new FlowRoutingMap.FlowRoutingMap(map)
  }
  test("opposite directions share corridor capacity while directed maps remain separate") {
    val state = PositionStateMap.getEmpty(graph).handleEventMovement(PersonSentEvent(person, forward, graph, 0, 0))
    assertEquals(state.capacityMap()(backward).onPosition.toList, List(person))
    assertEquals(state.capacityMapDirected()(backward).onPosition.toList, Nil)
    assertEquals(state.capacityMapDirected()(forward).onPosition.toList, List(person))
  }
  test("a full corridor blocks another person traveling in the opposite direction") {
    val other = EvaPerson(2, Seq(a))
    val occupied = PositionStateMap.getEmpty(graph).handleEventMovement(PersonSentEvent(person, forward, graph, 0, 0))
      .handleEventMovement(PersonInsertedEvent(other, b, graph, 0, 0))
    assertEquals(occupied.tryToSendPerson(routes(RoutingOption(b, Some(a), a, 100)), ClosestGoalStrategy), None)
  }
  test("receiving a person releases corridor capacity for waiting traffic") {
    val other = EvaPerson(2, Seq(a))
    val occupied = PositionStateMap.getEmpty(graph).handleEventMovement(PersonSentEvent(person, forward, graph, 0, 0))
      .handleEventMovement(PersonInsertedEvent(other, b, graph, 0, 0))
    val received = occupied.handleEventMovement(PersonReceivedEvent(person, forward, graph, 100, 0))
    val option = RoutingOption(b, Some(a), a, 100)
    assert(received.tryToSendPerson(routes(option), ClosestGoalStrategy).exists(_._2 == option))
    assertEquals(occupied.capacityMap()(forward).onPosition.size, 1)
  }
  test("routing a person with no route leaves the supplied routing map unchanged") {
    val positions = PositionStateMap.getEmpty(graph).handleEventMovement(PersonInsertedEvent(person, a, graph, 0, 0))
    val routing = routes()
    assertEquals(positions.tryToSendPerson(routing, ClosestGoalStrategy), None)
    assertEquals(routing.getMap.keys().toList, Nil)
  }
  test("exit occupants are never sent back into the graph") {
    val edge = new PositionableEdge(exit, a, ConnectionInfo(1, 100))
    val model = EvaGraphModel(List(exit, a), List(edge))
    val state = PositionStateMap.getEmpty(model).handleEventMovement(PersonInsertedEvent(person, exit, model, 0, 0))
    assertEquals(state.tryToSendPerson(routes(RoutingOption(exit, Some(a), a, 100)), ClosestGoalStrategy), None)
  }
  test("three people queue through a capacity-one exit without loss or over-capacity") {
    val edge = new PositionableEdge(a, exit, ConnectionInfo(1, 100))
    val model = EvaGraphModel(List(a, exit), List(edge))
    val persons = (1 to 3).map(n => EvaPerson(n, Seq(exit))).toSet
    val pending = persons.map(p => PersonInsertedEvent(p, a, model, 0, 0): it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent)
    val initial = EvacuationState(PositionStateMap.getEmpty(model), persons, routes(RoutingOption(a, Some(exit), exit, 100)), 0, Nil, pending)
    val states = List.newBuilder[EvacuationState]
    var current = Option(initial)
    var steps = 0
    while (current.nonEmpty && steps < 30) {
      val state = current.get
      states += state
      assert(state.curPositionsInState.capacityMap()(edge).onPosition.size <= 1)
      val waitingForInsertion = state.remainingEvents.collect { case e: PersonInsertedEvent => e.person }
      val accountedFor = state.curPositionsInState.positionStateMap.getAllValues ++ state.getSafePersons.getAllValues ++ waitingForInsertion
      assertEquals(accountedFor, persons)
      current = state.calculateNextState(ClosestGoalStrategy)
      steps += 1
    }
    assertEquals(current, None)
    val history = states.result()
    assertEquals(history.last.currenTimestamp, 300L)
    assertEquals(history.last.getSafePersons.getAllValues, persons)
    assertEquals(history.map(_.currenTimestamp), history.map(_.currenTimestamp).sorted)
  }
}
