package it.evadid.homepage.webElements.editor.evacuation

import it.evadid.core.datastructures.matrix.Neighbourhood
import it.evadid.evacuation.eva2.algorithm.escaping.*
import it.evadid.evacuation.eva2.algorithm.escaping.strategies.ClosestGoalStrategy
import it.evadid.evacuation.eva2.algorithm.escaping.strategies.ClosestGoalStrategy.{CGSimInfo, CGStepInfo}
import it.evadid.evacuation.eva2.control.modes.ScenarioPlayerMode
import it.evadid.workbook.model.evacuation.*
import munit.FunSuite

class EvacuationPlaybackSpec extends FunSuite {
  private val corridor = EvacuationFloorPlan(4, 1, List.fill(3)(EvacuationTile.Floor) :+ EvacuationTile.Exit, Set(0, 1))
  private def controller = new EvacuationPlayback(_ => () => ())
  private def simulate(plan: EvacuationFloorPlan, limit: Int = 250) = EvacuationStep.calculateEvacuation(
    EvacuationFloorAdapter.decode(plan), Neighbourhood.neumann,
    ClosestGoalStrategy(PersonOrderSelector.getIdSelector[CGSimInfo, CGStepInfo], stableRoutes = true), limit)
  test("EVA2 evacuates a corridor in physical movement steps without an extra exit-removal step") {
    val simulation = simulate(corridor)
    assertEquals(simulation.steps.distinct.count(_ > 0), 3)
    assert(simulation.states.last.persons.isEmpty)
    for (map <- simulation.states) assertEquals(map.persons.map(_.pos).size, map.persons.size)
    assertEquals(simulation, simulate(corridor))
    assert(EvacuationMetaData(simulation, 0, "neumann", "test").success)
  }
  test("blocked or exit-free scenarios retain an initial playback state and accurate failure metadata") {
    for (plan <- List(corridor.copy(tiles = List(EvacuationTile.Floor, EvacuationTile.Wall, EvacuationTile.Wall, EvacuationTile.Exit), people = Set(0)),
        corridor.copy(tiles = List.fill(4)(EvacuationTile.Floor)))) {
      val simulation = simulate(plan)
      assert(simulation.states.nonEmpty)
      assert(simulation.states.last.persons.nonEmpty)
      assert(!EvacuationMetaData(simulation, 0, "neumann", "test").success)
      val player = controller
      player.prepare(EvacuationExperiment(plan))
      assert(player.atEnd.now())
      assertEquals(player.measurement("Blocked").outcome, EvacuationOutcome.Blocked)
    }
  }
  test("zero-person and initially-safe scenarios succeed without movement steps") {
    for (people <- List(Set.empty[Int], Set(3))) {
      val player = controller
      player.prepare(EvacuationExperiment(corridor.copy(people = people)))
      assert(player.atEnd.now())
      assertEquals(player.measurement("Empty").steps, 0)
      assertEquals(player.measurement("Empty").outcome, EvacuationOutcome.Evacuated)
    }
  }
  test("engine stops at an explicit step budget and reports remaining persons") {
    val simulation = simulate(corridor, 1)
    assertEquals(simulation.steps.distinct.count(_ > 0), 1)
    assert(simulation.states.last.persons.nonEmpty)
    intercept[IllegalArgumentException](simulate(corridor, 0))
  }
  test("local player navigates, clamps invalid indices and restores only its injected floor") {
    val simulation = simulate(corridor)
    var shown = simulation.states.head
    var failed = false
    val mode = ScenarioPlayerMode(simulation.initialState, simulation, EvacuationMetaData(simulation, 0, "four", "stable"),
      map => shown = map, () => false, () => 0, () => (), () => failed = true)
    mode.changeStatus(_ => Int.MaxValue)
    assertEquals(shown, simulation.states.last)
    mode.changeStatus(_ => -100)
    assertEquals(shown, simulation.states.head)
    mode.changeStatus(simulation.nextStep)
    assert(mode.currentStateIndex > 0)
    mode.onLeavingMode()
    assertEquals(shown, simulation.initialState)
    mode.onEnteringMode()
    assert(!failed)
    assertEquals(mode.getDrawingInformation(), Map.empty)
  }
  test("playback records only at the end and independently supports reset/back/forward") {
    val player = controller
    player.prepare(EvacuationExperiment(corridor))
    intercept[IllegalArgumentException](player.measurement("Too early"))
    player.next(); assertEquals(player.step.now(), 1)
    player.previous(); assertEquals(player.step.now(), 0)
    while (!player.atEnd.now()) player.next()
    val run = player.measurement("  Completed  ")
    assertEquals(run.label, "Completed")
    assertEquals(run.steps, 3)
    assertEquals(run.remainingPeople, 0)
    assertEquals(run.floor, corridor)
    player.reset(); assertEquals(player.step.now(), 0)
    assert(!player.atEnd.now())
    player.clear(); assert(!player.ready.now())
  }
  test("scheduled playback cancels on pause and clear, including callbacks already queued") {
    var pending = List.empty[() => Unit]
    var cancelled = 0
    val player = new EvacuationPlayback(callback => { pending = pending :+ callback; () => cancelled += 1 })
    player.prepare(EvacuationExperiment(corridor))
    player.play(); assert(player.running.now())
    val old = pending.head
    player.pause(); old(); assertEquals(player.step.now(), 0)
    assert(cancelled > 0)
    player.play(); pending.last(); assertEquals(player.step.now(), 1)
    val stale = pending.last
    player.clear(); stale(); assertEquals(player.step.now(), 0)
    assert(!player.running.now())
  }
  test("four versus eight neighbours reuse EVA2 routing and preserve separate measurements") {
    val plan = EvacuationFloorPlan(2, 2, List(EvacuationTile.Floor, EvacuationTile.Floor, EvacuationTile.Floor, EvacuationTile.Exit), Set(0))
    val results = List(EvacuationNeighbourhood.Four, EvacuationNeighbourhood.Eight).map(neighbours => {
      val player = controller
      player.prepare(EvacuationExperiment(plan, EvacuationSettings(neighbours)))
      while (!player.atEnd.now()) player.next()
      player.measurement(neighbours.toString)
    })
    assertEquals(results.map(_.steps), List(2, 1))
  }
  test("browser preparation budgets reject oversized scenarios before creating playback") {
    for (plan <- List(EvacuationFloorPlan.empty(40, 40), EvacuationFloorPlan.empty(11, 10).copy(people = (0 until 101).toSet),
        EvacuationFloorPlan(9, 1, List.fill(9)(EvacuationTile.Exit)))) {
      val player = controller
      intercept[IllegalArgumentException](player.prepare(EvacuationExperiment(plan)))
      assert(!player.ready.now())
    }
  }
}
