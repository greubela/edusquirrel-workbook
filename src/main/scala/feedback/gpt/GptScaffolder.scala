package feedback.gpt

import workbook.model.feedback.FeedbackStatus.FINISHED
import workbook.model.feedback.scaffolding.BasicStringScaffoldingResult
import workbook.model.interaction.Scaffolder
import workbook.model.states.BasicVariableBasedState
import workbook.model.states.BasicVariableBasedState.BasicStringState

case class GptScaffolder() extends Scaffolder[BasicStringState, BasicStringScaffoldingResult] {

  private var counter = 0

  def generateFeedback(notifyOnGradingUpdate: BasicStringScaffoldingResult => Any) = {
    counter = counter + 1
    notifyOnGradingUpdate(new BasicStringScaffoldingResult("This is GPT Feedback #" + counter, FINISHED))
  }

  def getCurrentState(): BasicStringState = BasicVariableBasedState.createStringState("This is the initial state")

  def loadState(stateToLoad: BasicStringState): Unit = {}


}
