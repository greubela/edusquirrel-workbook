package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.ownership.Owner
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.Var
import contentmanagement.model.language.AppLanguage
import contentmanagement.model.language.AppLanguage.*
import contentmanagement.webElements.HtmlAppElement

case class HtmlBeProgramEditor(editorState: EditorState) extends HtmlAppElement {

  val strVar: Var[String] = Var(editorState.treeToEdit.now().fullProgram.getInLanguage(Python, English))
  editorState.treeToEdit.signal.foreach(tree => strVar.update(_ => tree.fullProgram.getInLanguage(Python, English)))(new Owner() {})
  var language: AppLanguage = AppLanguage.English

  override def getDomElement(): L.Element = div(
    cls := s"be-fullscreen-panel block-workspace",
    h2(
      cls := "be-fullscreen-panel-label",
      "Edit Program"
    ),
    div(
      cls := "be-fullscreen-panel-content",
      child <-- editorState.treeToDisplaySignal
    ))


/*
    button(
        "Sync: Python -> Blöcke!",
        onClick --> { _ =>
          editorState.treeToEdit.update(oldTree => {
            BeProgram(BeStartProgram(PythonParser.parsePython(strVar.now())))
          })
        } // event handler
      ),
      button(
        "Language: EN",
        onClick --> { _ =>
          strVar.update(oldStr => {
            editorState.treeToEdit.now().fullProgram.getInLanguage(Python, English)
          })
        } // event handler
      ),
      button(
        "Language: DE",
        onClick --> { _ =>
          strVar.update(oldStr => {
            editorState.treeToEdit.now().fullProgram.getInLanguage(Python, German)
          })
        }
 */


}
