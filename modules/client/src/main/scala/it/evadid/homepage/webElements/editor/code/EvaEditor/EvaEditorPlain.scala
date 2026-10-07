package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingState

case class EvaEditorPlain(
    override val state: Var[ProgrammingState],
    override val config: EvaEditorConfig,
    override val onStateEdited: ProgrammingState => Unit = _ => ()
) extends EvaEditor {

  override def furtherDomElements(): List[L.Element] = List()


}
