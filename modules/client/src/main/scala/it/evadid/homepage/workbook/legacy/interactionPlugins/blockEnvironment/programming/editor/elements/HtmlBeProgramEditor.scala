package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.ownership.Owner
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.programming.BeProgram
import todomove.datastructures.core.vm.code.others.BeStartProgram
import todomove.datastructures.core.vm.parsing.cpp.CppParser
import todomove.datastructures.core.vm.parsing.python.PythonParser
import todomove.webElementsOld.webElements.genericHtmlElements.editor.CodeMirrorEditor
import todomove.webElementsOld.webElements.genericHtmlElements.other.{HtmlTab, HtmlTabElement}

case class HtmlBeProgramEditor(
                                editorState: EditorState,
                                textLanguage: ProgrammingLanguage = Python
                              ) extends HtmlAppElement {

  private val parseWarningVar: Var[Option[String]] = Var(None)
  private val textDirtyVar: Var[Boolean] = Var(false)


  private val BlockViewTabNr = 0
  private val TextViewTabNr = 1

  private val strVar: Var[String] = Var(editorState.treeToEdit.now().fullProgram.expressionIO.getInLanguage(textLanguage, English, false))
  editorState.treeToEdit.signal.foreach { tree =>
    if (!textDirtyVar.now()) {
      strVar.set(tree.fullProgram.expressionIO.getInLanguage(textLanguage, English, false))
    }
  }(using new Owner() {})
  private var language: HumanLanguage = AppLanguage.English


  private def syncTextToBlocks(): Unit = {
    if (!textDirtyVar.now()) return

    val raw = strVar.now()
    val trimmed = raw.trim

    try {
      textLanguage match {
        case Python =>
          val parsedProgram = PythonParser.parsePython(raw)
          editorState.treeToEdit.set(BeProgram(BeStartProgram(parsedProgram)))
          textDirtyVar.set(false)
          parseWarningVar.set(None)

        case Cpp =>
          val result = CppParser.parseCppWithDiagnostics(raw)
          val unsupported = result.unsupportedStatements.filter(_.trim.nonEmpty)
          if (unsupported.nonEmpty) {
            val preview = unsupported.take(3).mkString(" | ")
            parseWarningVar.set(Some(s"C++ parser: unsupported statement(s) shown as red blocks. Example: $preview"))
          } else {
            parseWarningVar.set(None)
          }

          // Always update the blocks from the parsed program. Unsupported statements are preserved as red blocks.
          editorState.treeToEdit.set(BeProgram(BeStartProgram(result.sequence)))
          textDirtyVar.set(false)

        // probably not optimal to parse python again here, but ok for now
        case _ =>
          val parsedProgram = PythonParser.parsePython(raw)
          if (trimmed.isEmpty || parsedProgram.body.nonEmpty) {
            editorState.treeToEdit.set(BeProgram(BeStartProgram(parsedProgram)))
            textDirtyVar.set(false)
            parseWarningVar.set(None)
          } else {
            parseWarningVar.set(Some("Parser: no recognized statements; keeping existing blocks."))
          }
      }
    } catch {
      case _: Throwable =>
        // Never overwrite the current program on parse errors.
        parseWarningVar.set(Some("Parse error; keeping existing blocks."))
    }
  }

  private val blockViewTab = HtmlTab(
    BlockViewTabNr,
    div(
      cls := "be-program-editor__block-view",
      child <-- editorState.treeToDisplaySignal
    ),
    "Block View"
  )

  private val pythonEditor: HtmlAppElement =
    CodeMirrorEditor(
      strVar,
      onUserInput = _ => textDirtyVar.set(true)
    )

  private val pythonViewTab = HtmlTab(
    TextViewTabNr,
    div(
      cls := "be-program-editor__python-view",
      child <-- parseWarningVar.signal.map {
        case Some(msg) if msg.nonEmpty =>
          div(cls := "be-program-editor__parse-warning", msg)
        case _ =>
          emptyNode
      },
      pythonEditor.getDomElement()
    ),
    s"${textLanguage.name} View"
  )

  private val tabbedContent = HtmlTabElement(
    List(blockViewTab, pythonViewTab),
    onTabSwitched = (previous, next) => {
      if (next.tabNr == TextViewTabNr && previous.tabNr != TextViewTabNr) {
        // Only overwrite the text view if the user doesn't have unsynced edits.
        // avoids silently deleting unsupported C++ statements
        if (!textDirtyVar.now()) {
          val source = editorState.treeToEdit.now().fullProgram.expressionIO.getInLanguage(textLanguage, language, false)
          parseWarningVar.set(None)
          strVar.set(source)
        }
      } else if (next.tabNr == BlockViewTabNr && previous.tabNr == TextViewTabNr) {
        syncTextToBlocks()
      }
    }
  )

  override def getDomElement(): L.Element = div(
    cls := s"be-fullscreen-panel block-workspace",
    // If the user navigates away while in text view (without switching back to block view),
    // persist changes by syncing dirty text into the shared EditorState.
    onUnmountCallback(_ => syncTextToBlocks()),
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
