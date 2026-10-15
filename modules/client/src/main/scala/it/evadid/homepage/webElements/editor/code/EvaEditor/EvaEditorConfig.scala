package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import upickle.default.*

case class EvaEditorConfig(
                            //snapConfig: SnapCodeEditorConfig = SnapCodeEditorConfig(),
                            enabledLanguages: List[ProgrammingLanguage] = List(SnapLanguage, Python, Java),
                            snapConfig: SnapCodeEditorConfig = SnapCodeEditorConfig.EmbroideryTesting
                            //codeMirrorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Flowchart

                          ) derives ReadWriter

object EvaEditorConfig {
  def Default: EvaEditorConfig = EvaEditorConfig()


  def defaultEditorFor(logger: Logger, programmingLanguage: ProgrammingLanguage, editor: EvaEditor): EvaEditorTab[?] = programmingLanguage.match {

    case Python => CodeMirrorEditor.pythonEditorForEva(logger)
    case SnapLanguage => SnapCodeEditor.forEvaTab(logger, editor.config.snapConfig, editor.state.now())
    case Java =>  JavaFunctionBasedEditor.forEvaTab(logger, CodeMirrorEditorConfig.Default, editor.state.now(), editor)
    case _ => ???
  }

}