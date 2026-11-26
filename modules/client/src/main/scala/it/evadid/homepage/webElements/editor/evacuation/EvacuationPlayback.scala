package it.evadid.homepage.webElements.editor.evacuation

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.matrix.Neighbourhood
import it.evadid.evacuation.eva2.algorithm.escaping.*
import it.evadid.evacuation.eva2.algorithm.escaping.strategies.ClosestGoalStrategy
import it.evadid.evacuation.eva2.algorithm.escaping.strategies.ClosestGoalStrategy.{CGSimInfo, CGStepInfo}
import it.evadid.evacuation.eva2.configuration.ui.ShowMovementOption
import it.evadid.evacuation.eva2.control.modes.ScenarioPlayerMode
import it.evadid.evacuation.eva2.model.EvaFloorMap
import it.evadid.workbook.model.evacuation.*

/** Playback delegates state navigation to EVA2. Scheduler injection makes cancellation testable. */
class EvacuationPlayback(schedule: (() => Unit) => (() => Unit)) {
  val floor = Var(EvacuationFloorAdapter.decode(EvacuationFloorPlan.empty()))
  val step = Var(0)
  val ready = Var(false)
  val running = Var(false)
  val atEnd = Var(false)
  val result = Var(Option.empty[EvacuationMeasurement])
  private val player = Var(Option.empty[ScenarioPlayerMode])
  private val cancelScheduled = Var[() => Unit](() => ())
  /** Ticks of an earlier run see a newer generation and stop. */
  private val generation = Var(0)

  def pause(): Unit = {
    generation.update(_ + 1)
    cancelScheduled.now()()
    cancelScheduled.set(() => ())
    running.set(false)
  }
  def clear(): Unit = { pause(); player.set(None); ready.set(false); atEnd.set(false); result.set(None); step.set(0) }
  def prepare(value: EvacuationExperiment): Unit = {
    clear()
    require(value.floor.tiles.size <= 400 && value.floor.people.size <= 100 && value.floor.exitCount <= 8,
      "Playback supports at most 400 cells, 100 people and 8 exits")
    val initial = EvacuationFloorAdapter.decode(value.floor)
    val neighbours = if (value.settings.neighbourhood == EvacuationNeighbourhood.Four) Neighbourhood.neumann else Neighbourhood.moore
    val strategy = ClosestGoalStrategy(PersonOrderSelector.getIdSelector[CGSimInfo, CGStepInfo], stableRoutes = true)
    val simulation = EvacuationStep.calculateEvacuation(initial, neighbours, strategy, EvacuationExperiment.maxSteps)
    val totalSteps = simulation.steps.distinct.count(_ > 0)
    val remaining = simulation.states.last.persons.size
    val outcome = if (remaining == 0) EvacuationOutcome.Evacuated
      else if (totalSteps >= EvacuationExperiment.maxSteps) EvacuationOutcome.StepLimit else EvacuationOutcome.Blocked
    result.set(Some(EvacuationMeasurement("Run", value.floor, value.settings, totalSteps, remaining, outcome)))
    val mode = ScenarioPlayerMode(initial, simulation, EvacuationMetaData(simulation, 0, neighbours.name, "closest goal / stable order"),
      next => player.now().foreach { current =>
        floor.set(next)
        step.set(simulation.steps.distinct.count(i => i > 0 && i <= current.currentStateIndex))
        atEnd.set(current.currentStateIndex == simulation.states.size - 1)
      },
      () => false, () => ShowMovementOption.SHOW_NO_MOVEMENTS, () => (), () => ())
    player.set(Some(mode))
    ready.set(true)
    mode.changeStatus(_ => 0)
  }
  def next(): Unit = { pause(); advance() }
  private def advance(): Unit = player.now().foreach(mode => mode.changeStatus(mode.evacuationSimulation.nextStep))
  def previous(): Unit = { pause(); player.now().foreach(mode => mode.changeStatus(mode.evacuationSimulation.previousStep)) }
  def reset(): Unit = { pause(); player.now().foreach(_.changeStatus(_ => 0)) }
  def play(): Unit = if (ready.now() && !atEnd.now() && !running.now()) {
    running.set(true)
    val token = generation.now()
    def tick(): Unit = if (token == generation.now() && running.now()) {
      advance()
      if (atEnd.now()) pause() else cancelScheduled.set(schedule(() => tick()))
    }
    cancelScheduled.set(schedule(() => tick()))
  }
  def measurement(label: String): EvacuationMeasurement = {
    require(ready.now() && atEnd.now(), "Finish playback before recording the run")
    result.now().get.copy(label = label.trim)
  }
}
