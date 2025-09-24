package interactionPlugins.automaton

import workbook.model.feedback.FeedbackStatus
import workbook.model.feedback.FeedbackStatus.FINISHED
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.Scaffolder

import scala.collection.mutable.ListBuffer

case class AutomatonScaffoldingFeedback(
  stateWhenStarted: AutomatonScaffoldingState,
  status: FeedbackStatus,
  hints: List[String]
) extends ScaffoldingResult[AutomatonScaffoldingState]

class AutomatonScaffolder extends Scaffolder[AutomatonScaffoldingState, AutomatonScaffoldingFeedback] {

  private var currentState: AutomatonScaffoldingState = AutomatonScaffoldingState(
    AutomatonEditorState(AutomatonMode.Dfa, Vector.empty, Vector.empty)
  )

  override def loadState(stateToLoad: AutomatonScaffoldingState): Unit = currentState = stateToLoad

  override def getCurrentState(): AutomatonScaffoldingState = currentState

  override def generateFeedback(notifyOnGradingUpdate: AutomatonScaffoldingFeedback => Any): Unit = {
    val hints = buildHints(currentState.editorState)
    val feedback = AutomatonScaffoldingFeedback(currentState, FINISHED, hints)
    notifyOnGradingUpdate(feedback)
  }

  private def buildHints(state: AutomatonEditorState): List[String] = {
    val hints = ListBuffer[String]()
    if (state.nodes.isEmpty) {
      hints += "Add states to begin building the automaton."
    }
    if (!state.nodes.exists(_.isStart)) {
      hints += "Select a start state by right-clicking a node and choosing 'Set as start'."
    }
    if (!state.nodes.exists(_.isAccepting)) {
      hints += "Mark at least one state as accepting for successful inputs."
    }
    if (state.transitions.isEmpty && state.nodes.lengthCompare(1) > 0) {
      hints += "Connect the states with transitions that consume input symbols."
    }
    if (state.mode == AutomatonMode.Dfa) {
      val duplicate = state.transitions.groupBy(t => (t.fromStateId, t.symbols)).values.exists(_.size > 1)
      if (duplicate) {
        hints += "Deterministic automatons should not have duplicate transitions for the same symbol."
      }
    }
    if (hints.isEmpty) List("Great! The automaton structure looks consistent so far.") else hints.toList
  }
}
