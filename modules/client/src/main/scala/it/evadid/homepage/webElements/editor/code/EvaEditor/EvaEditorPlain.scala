package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

case class EvaEditorPlain(override val state: Var[ProgrammingState], override val config: EvaEditorConfig) extends EvaEditor {

  override def furtherDomElements(): List[L.Element] = List()


}
