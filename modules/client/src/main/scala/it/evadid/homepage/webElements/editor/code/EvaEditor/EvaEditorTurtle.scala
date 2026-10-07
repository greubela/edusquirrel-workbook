package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.HtmlTurtleRecreateShapeRenderer
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingState, TurtleGraphic}
import it.evadid.homepage.webElements.editor.code.JavaEditorSession

case class EvaEditorTurtle(
    override val state: Var[ProgrammingState],
    override val config: EvaEditorConfig,
    target: TurtleGraphic,
    override val onStateEdited: ProgrammingState => Unit = _ => (),
    override val javaRunnerFactory: () => JavaEditorSession.Runner = JavaEditorSession.defaultRunner
) extends EvaEditor {

  override def furtherDomElements(): List[Element] = List(
    div(
      cls := "turtle-sidebar",
      HtmlTurtleRecreateShapeRenderer.createInteractivePreview(state, target).getDomElement()
    )
  )


}
