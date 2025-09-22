package feedback.gpt

import workbook.model.feedback.FeedbackStatus.FINISHED
import workbook.model.states.BasicVariableBasedState.BasicStringState
import workbook.model.feedback.grading.BasicStringGradingResult
import workbook.model.feedback.grading.GradingGrade.UNKNOWN
import workbook.model.feedback.scaffolding.BasicStringScaffoldingResult
import workbook.model.interaction.Grader
import workbook.model.states.Stateless

case class GptGrader() extends Grader[BasicStringState, Stateless, BasicStringGradingResult]{

  private var counter = 0;
  
  override def loadState(stateToLoad: Stateless): Unit = {}

  override def getCurrentState(): Stateless = Stateless.StatelessInstance

  override def gradeState(editorStateToGrade: BasicStringState, notifyOnGradingUpdate: BasicStringGradingResult => Any): Unit = {
    counter = counter + 1
    notifyOnGradingUpdate(new BasicStringGradingResult("my opinion on >>>" + editorStateToGrade.getStateAsString() + "<<< is... mixed (#" + counter + "!", FINISHED, UNKNOWN))
  }

}
