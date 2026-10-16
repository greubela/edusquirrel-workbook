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
  def tabs(logger: Logger, config: EvaEditorConfig): List[EvaEditorTab] =
    config.enabledLanguages.distinct.map {
      case Python => CodeMirrorEditor.pythonEditorForEva(logger, config.codeMirrorConfig)
      case SnapLanguage => SnapCodeEditor.forEvaTab(logger, config.snapConfig)
      case Java => JavaFunctionBasedEditor.forEvaTab(logger, config.codeMirrorConfig)
      case language => throw UnsupportedOperationException(s"No editor is available for '$language'.")
    }
}
