package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.ownership.Owner
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.Var
import contentmanagement.model.language.AppLanguage
import contentmanagement.model.language.AppLanguage.*
import contentmanagement.model.vm.code.others.BeStartProgram
import contentmanagement.webElements.HtmlAppElement
import contentmanagement.webElements.genericHtmlElements.editor.CodeMirrorEditor
import contentmanagement.webElements.genericHtmlElements.other.{HtmlTab, HtmlTabElement}
import interactionPlugins.blockEnvironment.programming.BeProgram
import interactionPlugins.blockEnvironment.programming.editor.elements.HtmlBeProgramEditor.PythonParse

case class HtmlBeProgramEditor(editorState: EditorState) extends HtmlAppElement {

  private val BlockViewTabNr = 0
  private val PythonViewTabNr = 1

  val strVar: Var[String] = Var(editorState.treeToEdit.now().fullProgram.getInLanguage(Python, English))
  editorState.treeToEdit.signal.foreach(tree => strVar.update(_ => tree.fullProgram.getInLanguage(Python, English)))(new Owner() {})
  var language: AppLanguage = AppLanguage.English

  private val blockViewTab = HtmlTab(
    BlockViewTabNr,
    div(
      cls := "be-program-editor__block-view",
      child <-- editorState.treeToDisplaySignal
    ),
    "Block View"
  )

  private val pythonEditor = CodeMirrorEditor(strVar)
  private val pythonViewTab = HtmlTab(
    PythonViewTabNr,
    div(
      cls := "be-program-editor__python-view",
      pythonEditor.getDomElement()
    ),
    "Python View"
  )

  private val tabbedContent = HtmlTabElement(
    List(blockViewTab, pythonViewTab),
    onTabSwitched = (previous, next) => {
      if (next.tabNr == PythonViewTabNr && previous.tabNr != PythonViewTabNr) {
        val pythonSource = editorState.treeToEdit.now().fullProgram.getInLanguage(Python, language)
        strVar.set(pythonSource)
      } else if (next.tabNr == BlockViewTabNr && previous.tabNr == PythonViewTabNr) {
        val parsedProgram = PythonParse.parsePython(strVar.now())
        editorState.treeToEdit.set(BeProgram(BeStartProgram(parsedProgram)))
      }
    }
  )

  override def getDomElement(): L.Element = div(
    cls := s"be-fullscreen-panel block-workspace",
    h2(
      cls := "be-fullscreen-panel-label",
      "Edit Program"
    ),
    div(
      cls := "be-fullscreen-panel-content",
      tabbedContent.getDomElement()
    )
  )

}

object HtmlBeProgramEditor {

  object PythonParse {
    import contentmanagement.model.vm.parsing.python.PythonParser

    def parsePython(source: String) = PythonParser.parsePython(source)
  }

}
