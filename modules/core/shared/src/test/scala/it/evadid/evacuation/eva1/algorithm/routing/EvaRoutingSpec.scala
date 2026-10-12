package it.evadid.evacuation.eva1.algorithm.routing

import it.evadid.core.datastructures.graph.PositionableEdge
import it.evadid.evacuation.core.algorithm.routing.model.RoutingOption
import it.evadid.evacuation.core.datastructures.graphs.Position
import it.evadid.evacuation.eva1.model.evagraph.*
import it.evadid.evacuation.eva1.algorithm.events.eventtypes.*
import it.evadid.evacuation.eva1.algorithm.strategy.*
import munit.FunSuite
import upickle.default.*

class EvaRoutingSpec extends FunSuite {
  private val start = Router(Position(0, 0), 2, 10, false)
  private val exit = Router(Position(3, 4), 0, 10, true)
  private val edge = new PositionableEdge(start, exit, ConnectionInfo(2, 100))
  private val graph = EvaGraphModel(List(start, exit), List(edge))
  private val person = EvaPerson(7, Seq(exit))
  private def roundTrip[T: ReadWriter](value: T): Unit = {
    assertEquals(read[T](write(value)), value)
    assertEquals(readBinary[T](writeBinary(value)), value)
  }
  test("routing value models have JSON and binary default codecs") {
    roundTrip(start)
    roundTrip(ConnectionInfo(3, 125))
    roundTrip(person)
    roundTrip(EvaPerson(0, Nil))
    roundTrip(CapacityInformation(Seq(person), 2))
    roundTrip(RoutingOption(start, Some(exit), exit, 100.0))
  }
  test("router edits preserve unrelated fields and do not mutate the original") {
    assertEquals(start.changeX(8), start.copy(pos = Position(8, 0)))
    assertEquals(start.changeY(9), start.copy(pos = Position(0, 9)))
    assertEquals(start.changeInit(3), start.copy(initCapacity = 3))
    assertEquals(start.changeMax(11), start.copy(maxCapacity = 11))
    assertEquals(start.setExit(true), start.copy(isExit = true))
    assertEquals(start.pos, Position(0, 0))
  }
  test("connection metrics and edits preserve capacity and delay semantics") {
    val connection = ConnectionInfo(2, 1000)
    assertEquals(connection.capacityPerSecond, 2.0)
    assertEquals(connection.necessaryBufferTimeInMs, 500L)
    assertEquals(connection.pxSpeedPerSecond(50), 50.0)
    assertEquals(connection.changeParallelism(0), connection)
    assertEquals(connection.changeDelay(-1), connection)
    assertEquals(connection.changeSpeed(50, 0), connection)
    assertEquals(connection.changeParallelism(4), ConnectionInfo(4, 1000))
    assertEquals(connection.changeSpeed(50, 100), ConnectionInfo(2, 500))
  }
  test("connection delays retain fractional distance and avoid integer overflow") {
    assertEquals(ConnectionInfo.getConnectionDelayFromSpeed(0.5, 50), 10)
    assertEquals(ConnectionInfo.getConnectionDelayFromRouterDist(1.25), 25)
    assertEquals(ConnectionInfo.getConnectionDelayFromSpeed(3000000, 50), 60000000)
  }
  test("connection delay conversion rejects invalid distances and speeds") {
    for (dist <- List(-1.0, Double.NaN, Double.PositiveInfinity, 1e20)) intercept[IllegalArgumentException](ConnectionInfo.getConnectionDelayFromSpeed(dist, 50))
    for (speed <- List(0, -1)) intercept[IllegalArgumentException](ConnectionInfo.getConnectionDelayFromSpeed(10, speed))
  }
  test("empty positions are readable without creating buckets") {
    val state = PositionStateMap.getEmpty(graph)
    assertEquals(state.getPersonAtPositions(EvaGraphTypes.routerToEither(start)).toList, Nil)
    assertEquals(state.positionStateMap.keys().size, 0)
  }
  test("movement events create independent snapshots and conserve the person until finished") {
    val empty = PositionStateMap.getEmpty(graph)
    val inserted = empty.handleEventMovement(PersonInsertedEvent(person, start, graph, 0, 0))
    val sent = inserted.handleEventMovement(PersonSentEvent(person, edge, graph, 0, 0))
    val received = sent.handleEventMovement(PersonReceivedEvent(person, edge, graph, 100, 0))
    val finished = received.handleEventMovement(PersonFinishedEvent(person, exit, graph, 100, 0))
    assertEquals(empty.positionStateMap.getAllEntries.size, 0)
    assertEquals(inserted.routerMap().get(start).get.toList, List(person))
    assertEquals(sent.edgesMap().get(edge).get.toList, List(person))
    assertEquals(received.routerMap().get(exit).get.toList, List(person))
    assertEquals(finished.positionStateMap.getAllEntries.size, 0)
    assertEquals(sent.capacityMap()(edge), CapacityInformation(Seq(person), 2))
    assertEquals(sent.capacityMapDirected()(edge), CapacityInformation(Seq(person), 2))
  }
  test("closest-goal strategy waits when only longer routes are free") {
    val short = RoutingOption(start, Some(exit), exit, 10)
    val long = short.copy(remainingDistance = 12)
    assertEquals(ClosestGoalStrategy.decideRouting(Seq(short, long), Seq(long)), None)
    assertEquals(ClosestGoalStrategy.decideRouting(Seq(short, long), Seq(short, long)), Some(short))
  }
  test("multiple-goal strategy accepts alternatives up to its threshold") {
    val short = RoutingOption(start, Some(exit), exit, 10)
    val alternative = short.copy(remainingDistance = 13)
    val strategy = new MultipleGoalStrategy(1.3)
    assertEquals(strategy.decideRouting(Seq(short, alternative), Seq(alternative)), Some(alternative))
    assertEquals(strategy.decideRouting(Seq(short, alternative), Seq(alternative.copy(remainingDistance = 13.1))), None)
  }
  test("strategies return no route when there are no available choices") {
    val route = RoutingOption(start, Some(exit), exit, 10)
    for (strategy <- List(ClosestGoalStrategy, new MultipleGoalStrategy())) {
      assertEquals(strategy.decideRouting(Nil, Nil), None)
      assertEquals(strategy.decideRouting(Seq(route), Nil), None)
    }
  }
  test("event-driven evacuation delivers a person to safety and terminates") {
    val routes = new it.evadid.evacuation.core.datastructures.maps.MultiHashMapList[Router, RoutingOption[Router]]()
    routes.addElement(start, RoutingOption(start, Some(exit), exit, 100))
    val routing = new FlowRoutingMap.FlowRoutingMap(routes)
    val insertion = PersonInsertedEvent(person, start, graph, 0, 0)
    val initial = EvacuationState(PositionStateMap.getEmpty(graph), Set(person), routing, 0, Nil, Set(insertion))
    val states = List.iterate(initial, 5)(_.calculateNextState(ClosestGoalStrategy).get)
    assertEquals(states.map(_.currenTimestamp), List(0L, 0L, 0L, 100L, 100L))
    val finished = states.last
    assertEquals(finished.getSafePersons.get(exit).get.toList, List(person))
    assertEquals(finished.curPositionsInState.positionStateMap.getAllEntries.size, 0)
    assertEquals(finished.calculateNextState(ClosestGoalStrategy), None)
    assertEquals(initial.handledEvents, Nil)
  }
  test("last activities follow simulation time and resolve simultaneous events by history order") {
    val insertion = new PersonInsertedEvent(person, start, graph, 0, 0) { override val timestampInMs = 9999L }
    val received = new PersonReceivedEvent(person, edge, graph, 100, 0) { override val timestampInMs = 9998L }
    val finished = new PersonFinishedEvent(person, exit, graph, 100, 0) { override val timestampInMs = 1L }
    val routes = new it.evadid.evacuation.core.datastructures.maps.MultiHashMapList[Router, RoutingOption[Router]]()
    val state = EvacuationState(PositionStateMap.getEmpty(graph), Set(person), new FlowRoutingMap.FlowRoutingMap(routes), 100,
      List(insertion, received, finished), Set.empty)
    assertEquals(state.lastEventMap()(person), finished)
  }

  test("person event codecs preserve every built-in event and its graph snapshot") {
    import it.evadid.evacuation.eva1.algorithm.events.traits.PersonEvent
    val events: List[PersonEvent] = List(
      PersonInsertedEvent(person, start, graph, 0, 0), PersonSentEvent(person, edge, graph, 10, 0),
      PersonReceivedEvent(person, edge, graph, 110, 0), PersonFinishedEvent(person, exit, graph, 110, 0))
    events.foreach { event =>
      for (decoded <- List(read[PersonEvent](write(event)), readBinary[PersonEvent](writeBinary(event)))) {
        assertEquals(decoded.getClass, event.getClass)
        assertEquals(decoded.person, event.person)
        assertEquals(decoded.eventStartTimestamp, event.eventStartTimestamp)
        assertEquals(decoded.graph.nodes.toSet, graph.nodes.toSet)
        assertEquals(decoded.graph.edges.toSet, graph.edges.toSet)
        assertEquals(decoded.graph.getDistFromEdge(edge), 100.0)
      }
    }
    intercept[Exception](read[PersonEvent]("""{"kind":"unknown","payload":null}"""))
  }

  test("mutable multi-map codecs preserve empty buckets, duplicates and independent state") {
    import it.evadid.evacuation.core.datastructures.maps.MultiHashMapList
    val original = new MultiHashMapList[String, Int]
    original("empty")
    original("items") ++= List(3, 1, 3)
    for (decoded <- List(read[MultiHashMapList[String, Int]](write(original)),
      readBinary[MultiHashMapList[String, Int]](writeBinary(original)))) {
      assertEquals(decoded, original)
      assert(decoded.contains("empty"))
      assertEquals(decoded("items").toList, List(3, 1, 3))
      decoded("items") += 99
      assertEquals(original("items").toList, List(3, 1, 3))
    }
  }

  test("a decoded evacuation snapshot resumes with identical routing and safe persons") {
    val routes = new it.evadid.evacuation.core.datastructures.maps.MultiHashMapList[Router, RoutingOption[Router]]()
    routes.addElement(start, RoutingOption(start, Some(exit), exit, 100))
    val initial = EvacuationState(PositionStateMap.getEmpty(graph), Set(person),
      new FlowRoutingMap.FlowRoutingMap(routes), 0, Nil, Set(PersonInsertedEvent(person, start, graph, 0, 0)))
    val sent = initial.calculateNextState(ClosestGoalStrategy).get.calculateNextState(ClosestGoalStrategy).get
    val expected = sent.calculateNextState(ClosestGoalStrategy).get.calculateNextState(ClosestGoalStrategy).get
    for (decoded <- List(read[EvacuationState](write(sent)), readBinary[EvacuationState](writeBinary(sent)))) {
      assertEquals(decoded.curPositionsInState.positionStateMap, sent.curPositionsInState.positionStateMap)
      val finished = decoded.calculateNextState(ClosestGoalStrategy).get.calculateNextState(ClosestGoalStrategy).get
      assertEquals(finished.currenTimestamp, expected.currenTimestamp)
      assertEquals(finished.getSafePersons, expected.getSafePersons)
      assertEquals(finished.curPositionsInState.positionStateMap.getAllEntries.size, 0)
      assertEquals(finished.calculateNextState(ClosestGoalStrategy), None)
    }
  }

  test("graph snapshots reject edge weights that differ from connection delays") {
    val custom: EvaGraphTypes.EvaGraph = new EvaGraphModel(List(start, exit), List(edge)) {
      override def getDistFromEdge(edge: PositionableEdge[Router, ConnectionInfo]): Double = 1.0
    }
    intercept[IllegalArgumentException](write[EvaGraphTypes.EvaGraph](custom))
  }

}
