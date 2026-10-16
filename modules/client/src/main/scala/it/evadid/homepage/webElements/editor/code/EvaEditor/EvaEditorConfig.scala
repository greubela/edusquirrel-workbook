package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import upickle.default.ReadWriter

case class EvaEditorConfig(
    enabledLanguages: List[ProgrammingLanguage] = List(SnapLanguage, Python, Java),
    snapConfig: SnapCodeEditorConfig = SnapCodeEditorConfig.EmbroideryTesting,
    codeMirrorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Flowchart
) derives ReadWriter

object EvaEditorConfig {
  def Default: EvaEditorConfig = EvaEditorConfig()
  def defaultEditorFor(logger: Logger, language: ProgrammingLanguage, config: EvaEditorConfig,
      source: ProgrammingState, appearance: EditorAppearance): EvaEditorTab[?] = language match {
    case Python => CodeMirrorEditor.pythonEditorForEva(logger, config.codeMirrorConfig, Some(appearance))
    case SnapLanguage => SnapCodeEditor.forEvaTab(logger, config.snapConfig, source)
    case Java => JavaFunctionBasedEditor.forEvaTab(logger, config.codeMirrorConfig, source, Some(appearance))
    case _ => throw UnsupportedOperationException(s"No editor is available for '$language'.")
  }
}
