package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorExtension
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}

final class TurtleTaskEditorExtension(
    state: Var[ProgrammingState],
    target: Option[TurtleGraphic],
    policy: TurtleDrawingPolicy
) extends EvaEditorExtension {
  private var view = Option.empty[TurtleExecutionPanel]

  override def panel(context: EvaEditorExtension.Context): Option[Element] = {
    val current = view.getOrElse {
      var captured = state.now()
      val created = new TurtleExecutionPanel(state, () => context.execute(captured), context.cancel,
        target, policy, () => { captured = context.captureSource() })
      view = Some(created)
      created
    }
    Some(div(cls := "eva-editor__sidebar turtle-sidebar", current.getDomElement()))
  }

  override def close(): Unit = view.foreach(_.reset())
}
