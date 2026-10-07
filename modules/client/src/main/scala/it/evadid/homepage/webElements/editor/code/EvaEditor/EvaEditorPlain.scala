package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingState
import it.evadid.homepage.webElements.editor.code.JavaEditorSession

case class EvaEditorPlain(
    override val state: Var[ProgrammingState],
    override val config: EvaEditorConfig,
    override val onStateEdited: ProgrammingState => Unit = _ => (),
    override val javaRunnerFactory: () => JavaEditorSession.Runner = JavaEditorSession.defaultRunner
) extends EvaEditor {

  override def furtherDomElements(): List[L.Element] = List()


}
