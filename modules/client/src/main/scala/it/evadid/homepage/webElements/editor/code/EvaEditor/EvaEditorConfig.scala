package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.core.datastructures.language.AppLanguage.*
import com.raquo.laminar.api.L.{Signal, Val}
import it.evadid.homepage.webElements.code.{JavaStructureFeatures, JavaStructuredMethodEditor, PythonStructuredMethodEditor}
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
                            codeMirrorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Flowchart,
                            javaStructure: JavaStructureFeatures = JavaStructureFeatures.Full
                          ) extends WebEditorConfig derives ReadWriter {

  override protected def additionalCssClasses: List[String] = List()
}

object EvaEditorConfig {
  def Default: EvaEditorConfig = EvaEditorConfig()

  def tabs(logger: Logger, config: EvaEditorConfig, labels: EvaEditorLabels,
           humanLanguage: Signal[HumanLanguage] = Val(English)): Seq[EvaEditorTab] =
    config.enabledLanguages.distinct.map {
      // Python and Java share the structure editor; `javaStructure` sets the diagram's actions for both.
      case Python => PythonStructuredMethodEditor.forEvaTab(logger, config.codeMirrorConfig, labels = labels.python, features = config.javaStructure)
      case SnapLanguage => SnapCodeEditor.forEvaTab(logger, config.snapConfig, humanLanguage)
      case Java => JavaStructuredMethodEditor.forEvaTab(logger, config.codeMirrorConfig, labels = labels.java, features = config.javaStructure)
      case BlockDisplay => BlockProgramEditor.forEvaTab(labels.blocks, humanLanguage)
      case language => throw UnsupportedOperationException(s"No editor is available for '$language'.")
    }
}
