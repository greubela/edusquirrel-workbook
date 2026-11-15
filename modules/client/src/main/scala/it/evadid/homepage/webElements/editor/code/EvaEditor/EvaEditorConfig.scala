package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.core.datastructures.language.AppLanguage.*
import com.raquo.laminar.api.L.{Signal, Val}
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.abstractions.WebEditorConfig
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import upickle.default.*

case class EvaEditorConfig(
                            enabledLanguages: Seq[ProgrammingLanguage] = List(SnapLanguage, Python, Java),
                            snapConfig: SnapCodeEditorConfig = SnapCodeEditorConfig.PythonCompatibleTesting,
                            codeMirrorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Flowchart
                          ) extends WebEditorConfig derives ReadWriter {

  override protected def additionalCssClasses: List[String] = List()
}

object EvaEditorConfig {
  def Default: EvaEditorConfig = EvaEditorConfig()

  def tabs(logger: Logger, config: EvaEditorConfig, labels: EvaEditorLabels,
           humanLanguage: Signal[HumanLanguage] = Val(English)): Seq[EvaEditorTab] =
    config.enabledLanguages.distinct.map {
      case Python => CodeMirrorEditor.pythonEditorForEva(logger, config.codeMirrorConfig)
      case SnapLanguage => SnapCodeEditor.forEvaTab(logger, config.snapConfig, humanLanguage)
      case Java => JavaFunctionBasedEditor.forEvaTab(logger, config.codeMirrorConfig)
      case language => throw UnsupportedOperationException(s"No editor is available for '$language'.")
    }
}
