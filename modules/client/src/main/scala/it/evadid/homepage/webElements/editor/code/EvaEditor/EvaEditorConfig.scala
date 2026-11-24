package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.core.datastructures.language.AppLanguage.*
import com.raquo.laminar.api.L.{Signal, Val}
import it.evadid.homepage.webElements.code.JavaComponentEditor
import it.evadid.homepage.webElements.editor.blocks.BlockProgramEditor
import it.evadid.homepage.webElements.editor.abstractions.WebEditorConfig
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import upickle.default.*

case class EvaEditorConfig(
                            enabledLanguages: Seq[ProgrammingLanguage] = List(SnapLanguage, Python, Java, BlockDisplay),
                            snapConfig: SnapCodeEditorConfig = SnapCodeEditorConfig.PythonCompatibleTesting,
                            codeMirrorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Flowchart
                          ) extends WebEditorConfig derives ReadWriter {

  override protected def additionalCssClasses: List[String] = List()
}

object EvaEditorConfig {
  def Default: EvaEditorConfig = EvaEditorConfig()

  /** The language choices `config` enables, in its order. Editors receive logging, labels and the display
    * language through their [[EvaEditorContext]] when created.
    */
  def tabs(config: EvaEditorConfig): Seq[EvaEditorTab] =
    config.enabledLanguages.distinct.map {
      case Python => CodeMirrorEditor.pythonEditorForEva(config.codeMirrorConfig)
      case SnapLanguage => SnapCodeEditor.forEvaTab(config.snapConfig)
      case Java => JavaComponentEditor.forEvaTab(config.codeMirrorConfig)
      case BlockDisplay => BlockProgramEditor.forEvaTab()
      case language => throw UnsupportedOperationException(s"No editor is available for '$language'.")
    }
}
