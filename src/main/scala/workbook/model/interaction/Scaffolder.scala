package workbook.model.interaction

import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.states.InteractionState

trait Scaffolder[ScaffoldingState <: InteractionState, SR <: ScaffoldingResult[ScaffoldingState]] {
  def loadState(stateToLoad: ScaffoldingState): Unit

  def getCurrentState(): ScaffoldingState

  def generateFeedback(notifyOnGradingUpdate: SR => Any): Unit
}
