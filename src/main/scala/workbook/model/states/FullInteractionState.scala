package workbook.model.states

case class FullInteractionState[EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState]
(editorState: EditorState, scaffoldingState: ScaffoldingState, gradingState: GradingState) extends InteractionState {
  def getStateAsString(): String = s"InteractionState:\n  Editor state: $editorState\n  Scaffolding state: $scaffoldingState\n  Grading state: $gradingState"
}
