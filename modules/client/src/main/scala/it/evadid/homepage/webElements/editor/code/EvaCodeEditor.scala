package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import it.evadid.core.datastructures.state.State
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.python.CodeMirrorEditor
import it.evadid.vm.BeProgram

case class EvaCodeEditor(state: State[BeProgram]) extends HtmlAppElement {

  println("[UGLY WARN FROM EVA CODE EDITOR] This is a temporary hack to get the editor to work, using dummy code mirror editor currently.")

  private lazy val codeMirrorPython = CodeMirrorEditor(Var("print('hello world')"))

  override def getDomElement(): L.Element = codeMirrorPython.getDomElement()


}
