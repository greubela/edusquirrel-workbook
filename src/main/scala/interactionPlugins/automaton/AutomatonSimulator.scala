package interactionPlugins.automaton

import com.raquo.airstream.state.Var

import scala.collection.mutable
import scala.scalajs.js.timers.{SetIntervalHandle, clearInterval, setInterval}

case class AutomatonSimulationStep(
  stepIndex: Int,
  consumed: String,
  remaining: String,
  activeStateIds: Set[String],
  triggeredBy: Option[String],
  isAccepting: Boolean
)

case class AutomatonSimulation(input: String, steps: Vector[AutomatonSimulationStep]) {
  lazy val isAccepted: Boolean = steps.lastOption.exists(step => step.remaining.isEmpty && step.isAccepting)

  def stepAt(index: Int): AutomatonSimulationStep = {
    val safeIndex = math.max(0, math.min(index, steps.length - 1))
    steps.lift(safeIndex).getOrElse(AutomatonSimulationStep(0, "", input, Set.empty, None, isAccepting = false))
  }
}

object AutomatonSimulator {

  private def buildTransitionsBySource(
    editorState: AutomatonEditorState
  ): Map[String, Vector[AutomatonTransition]] =
    editorState.transitions.groupBy(_.fromStateId).view.mapValues(_.toVector).toMap

  private def epsilonClosure(
    start: Set[String],
    transitionsBySource: Map[String, Vector[AutomatonTransition]]
  ): Set[String] = {
    if (start.isEmpty) Set.empty
    else {
      val visited = mutable.Set[String]()
      val pending = mutable.Queue[String]()
      pending.enqueueAll(start)
      while (pending.nonEmpty) {
        val current = pending.dequeue()
        if (!visited.contains(current)) {
          visited += current
          transitionsBySource
            .getOrElse(current, Vector.empty)
            .foreach { transition =>
              if (transition.symbols.exists(AutomatonAlgorithms.isEpsilon)) {
                pending.enqueue(transition.toStateId)
              }
            }
        }
      }
      visited.toSet
    }
  }

  private def advanceStates(
    activeStates: Set[String],
    symbol: String,
    transitionsBySource: Map[String, Vector[AutomatonTransition]]
  ): Set[String] = {
    if (activeStates.isEmpty) Set.empty
    else {
      val directTargets = activeStates.flatMap { fromStateId =>
        transitionsBySource
          .getOrElse(fromStateId, Vector.empty)
          .collect {
            case transition if transition.symbols.exists(sym => !AutomatonAlgorithms.isEpsilon(sym) && sym == symbol) =>
              transition.toStateId
          }
      }
      epsilonClosure(directTargets, transitionsBySource)
    }
  }

  def simulate(editorState: AutomatonEditorState, input: String): AutomatonSimulation = {
    val nodeMap = editorState.nodeMap
    val transitionsBySource = buildTransitionsBySource(editorState)
    val startStates = nodeMap.values.filter(_.isStart).map(_.id).toSet
    var activeStates: Set[String] = epsilonClosure(startStates, transitionsBySource)

    val stepsBuilder = Vector.newBuilder[AutomatonSimulationStep]
    val initialAccepting = activeStates.exists(id => nodeMap.get(id).exists(_.isAccepting))
    stepsBuilder += AutomatonSimulationStep(0, "", input, activeStates, None, initialAccepting)

    var consumed = ""
    var remaining = input
    var index = 1
    input.foreach { char =>
      val symbol = char.toString
      val nextStates = advanceStates(activeStates, symbol, transitionsBySource)
      activeStates = nextStates
      consumed = consumed + symbol
      remaining = remaining.drop(1)
      val isAccepting = activeStates.exists(id => nodeMap.get(id).exists(_.isAccepting))
      stepsBuilder += AutomatonSimulationStep(index, consumed, remaining, activeStates, Some(symbol), isAccepting)
      index += 1
    }

    val steps = stepsBuilder.result()
    AutomatonSimulation(input, steps)
  }

  def acceptsWord(editorState: AutomatonEditorState, word: String): Boolean =
    simulate(editorState, word).isAccepted
}

class AutomatonSimulationController(initialState: AutomatonEditorState) {

  private var editorState: AutomatonEditorState = initialState
  private var currentInput: String = ""
  private var currentSimulation: AutomatonSimulation = AutomatonSimulator.simulate(initialState, currentInput)
  private var currentStepIndex: Int = 0
  private var playbackHandle: Option[SetIntervalHandle] = None
  private var playing: Boolean = false

  val inputVar: Var[String] = Var(currentInput)
  val simulationVar: Var[AutomatonSimulation] = Var(currentSimulation)
  val stepIndexVar: Var[Int] = Var(currentStepIndex)
  val isPlayingVar: Var[Boolean] = Var(playing)

  def onEditorStateChanged(newState: AutomatonEditorState): Unit = {
    editorState = newState
    recomputeSimulation(resetStep = false)
  }

  def setInput(value: String): Unit = {
    currentInput = value
    inputVar.set(value)
    recomputeSimulation(resetStep = true)
  }

  def reset(): Unit = {
    pause()
    currentStepIndex = 0
    stepIndexVar.set(0)
  }

  def stepForward(): Unit = {
    if (currentStepIndex < currentSimulation.steps.length - 1) {
      currentStepIndex += 1
      stepIndexVar.set(currentStepIndex)
    }
  }

  def stepBackward(): Unit = {
    if (currentStepIndex > 0) {
      currentStepIndex -= 1
      stepIndexVar.set(currentStepIndex)
    }
  }

  def play(): Unit = {
    if (playing) return
    playing = true
    isPlayingVar.set(true)
    playbackHandle = Some(setInterval(600) {
      if (currentStepIndex < currentSimulation.steps.length - 1) stepForward()
      else pause()
    })
  }

  def pause(): Unit = {
    playing = false
    isPlayingVar.set(false)
    playbackHandle.foreach(clearInterval)
    playbackHandle = None
  }

  def currentStepSignal = simulationVar.signal.combineWithFn(stepIndexVar.signal)((simulation, step) => simulation.stepAt(step))

  private def recomputeSimulation(resetStep: Boolean): Unit = {
    pause()
    currentSimulation = AutomatonSimulator.simulate(editorState, currentInput)
    val maxIndex = math.max(0, currentSimulation.steps.length - 1)
    if (resetStep) currentStepIndex = 0
    currentStepIndex = math.min(currentStepIndex, maxIndex)
    simulationVar.set(currentSimulation)
    stepIndexVar.set(currentStepIndex)
  }
}
