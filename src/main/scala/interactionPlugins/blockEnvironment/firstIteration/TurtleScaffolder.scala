package interactionPlugins.blockEnvironment.firstIteration

import workbook.model.feedback.FeedbackStatus
import workbook.model.feedback.FeedbackStatus.FINISHED
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.Scaffolder

case class TurtleScaffoldingFeedback(
  stateWhenStarted: TurtleScaffoldingState,
  diff: TurtleProgramDiff,
  status: FeedbackStatus,
  message: String
) extends ScaffoldingResult[TurtleScaffoldingState]

class TurtleScaffolder(sampleProgram: TurtleProgramState) extends Scaffolder[TurtleScaffoldingState, TurtleScaffoldingFeedback] {

  private var currentState: TurtleScaffoldingState =
    TurtleScaffoldingState(TurtleProgramState.empty, sampleProgram)

  override def loadState(stateToLoad: TurtleScaffoldingState): Unit = {
    currentState = stateToLoad
  }

  override def getCurrentState(): TurtleScaffoldingState = currentState

  override def generateFeedback(notifyOnGradingUpdate: TurtleScaffoldingFeedback => Any): Unit = {
    val diff = TurtleProgramDiff.compare(currentState.sampleProgram, currentState.currentProgram)
    val message = if (diff.isPerfectMatch) "The current program matches the sample solution." else diff.humanReadableSummary
    val feedback = TurtleScaffoldingFeedback(currentState, diff, FINISHED, message)
    notifyOnGradingUpdate(feedback)
  }
}
