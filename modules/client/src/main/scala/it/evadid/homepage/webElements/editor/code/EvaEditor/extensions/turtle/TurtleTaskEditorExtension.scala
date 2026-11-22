package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.CompareShapePreview
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.elements.interactionElements.programming.state.*

final class TurtleTaskEditorExtension(
    state: Var[ProgrammingState], target: Option[TurtleGraphic], sharedExecution: Boolean = false,
    comparisonPolicy: TurtleDrawingPolicy = TurtleDrawingPolicy.Strokes
) extends it.evadid.homepage.webElements.HtmlAppElement {
  // Java runs on the shared BeExpression interpreter, as converted programs do.
  private val execution = new TurtleProgramExecution(sharedInterpreter = sharedExecution)
  private lazy val panel = new TurtleExecutionPanel(state, () => execution.commandsFor(state.now()), () => execution.cancel(), target, comparisonPolicy)
  private lazy val preview = target.map(CompareShapePreview(state, _, comparisonPolicy))
  override def getDomElement(): Element =
    // With a target every language previews live; without one the program is run on request.
    div(cls := "eva-editor__sidebar turtle-sidebar",
      preview.fold(panel.getDomElement())(live => div(cls := "turtle-live-preview", live.getDomElement())),
      onUnmountCallback(_ => execution.close()))
}
