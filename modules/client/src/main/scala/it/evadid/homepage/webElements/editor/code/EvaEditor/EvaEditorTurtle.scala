package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.HtmlTurtleRecreateShapeRenderer
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingState, TurtleGraphic}

case class EvaEditorTurtle(override val state: Var[ProgrammingState], override val config: EvaEditorConfig, target: TurtleGraphic) extends EvaEditor {

  override def furtherDomElements(): List[Element] = List(
    div(
      cls := "turtle-sidebar",
      HtmlTurtleRecreateShapeRenderer.createInteractivePreview(state, target).getDomElement()
    )
  )


}
