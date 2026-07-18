package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.SnapRenderer.BeProgramSnapRenderer
import it.evadid.vm.BeProgram

case class EvaCodeEditor(state: State[BeProgram]) extends HtmlAppElement {

  private lazy val programVar: Var[BeProgram] = state.toAirstreamVar

  override def getDomElement(): L.Element = BeProgramSnapRenderer(programVar).getDomElement()


}
