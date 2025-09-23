package workbook.model.feedback.scaffolding

import workbook.model.feedback.FeedbackStatus
import workbook.model.states.BasicVariableBasedState.BasicStringState
import workbook.model.states.InteractionState

case class BasicVariableScaffoldingResult[T, ScaffoldingState <: InteractionState](stateWhenStarted: ScaffoldingState, variable: T, status: FeedbackStatus) extends ScaffoldingResult[ScaffoldingState] {

}


type GptScaffoldingResult = BasicVariableScaffoldingResult[String, BasicStringState]
