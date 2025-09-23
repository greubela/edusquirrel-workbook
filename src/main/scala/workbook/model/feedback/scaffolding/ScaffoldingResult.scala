package workbook.model.feedback.scaffolding

import workbook.model.feedback.FeedbackResult
import workbook.model.states.InteractionState

trait ScaffoldingResult[ScaffoldingState <: InteractionState] extends FeedbackResult {
  val stateWhenStarted: ScaffoldingState
}
