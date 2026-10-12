package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.HtmlTurtleRecreateShapeRenderer
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic

case class EvaEditorTurtle(override val state: Var[ProgrammingState], override val config: EvaEditorConfig, target: TurtleGraphic) extends EvaEditor {

  override def furtherDomElements(): List[Element] = List(
    div(
      cls := "eva-editor__sidebar turtle-sidebar",
      HtmlTurtleRecreateShapeRenderer.createInteractivePreview(state, target).getDomElement()
    )
  )


}
