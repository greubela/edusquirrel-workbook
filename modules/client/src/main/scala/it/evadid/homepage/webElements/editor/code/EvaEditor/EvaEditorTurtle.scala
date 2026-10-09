package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.HtmlTurtleRecreateShapeRenderer
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingState, TurtleGraphic}

case class EvaEditorTurtle(
    override val state: Var[ProgrammingState],
    override val config: EvaEditorConfig,
    target: TurtleGraphic,
    override val onStateEdited: ProgrammingState => Unit = _ => (),
    override val extensions: List[EvaEditorExtension] = Nil
) extends EvaEditor {

  override def furtherDomElements(): List[Element] = List(
    div(
      cls := "eva-editor__sidebar turtle-sidebar",
      display <-- activeTab.signal.map(tab => if tab == EvaEditor.Tab.Java then "none" else "block"),
      HtmlTurtleRecreateShapeRenderer.createInteractivePreview(state, target).getDomElement()
    )
  )


}
