package plugins.gpt

import workbook.model.feedback.FeedbackStatus.FINISHED
import workbook.model.feedback.scaffolding.GptScaffoldingResult
import workbook.model.interaction.Scaffolder
import workbook.model.states.BasicVariableBasedState
import workbook.model.states.BasicVariableBasedState.BasicStringState

case class GptScaffolder() extends Scaffolder[BasicStringState, GptScaffoldingResult] {

  private var counter = 0

  def generateFeedback(notifyOnGradingUpdate: GptScaffoldingResult => Any) = {
    counter = counter + 1
    notifyOnGradingUpdate(new GptScaffoldingResult(getCurrentState(), "This is GPT Feedback #" + counter + " for: \n>>>" + getCurrentState() + "<<<", FINISHED))
  }

  def getCurrentState(): BasicStringState = BasicVariableBasedState.createStringState("This is the initial state")

  def loadState(stateToLoad: BasicStringState): Unit = {}


}
