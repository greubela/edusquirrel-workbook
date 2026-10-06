package it.evadid.homepage.webElements.editor.code

import it.evadid.core.datastructures.language.AppLanguage.{English, Python, Java, ProgrammingLanguage}
import it.evadid.homepage.webElements.editor.code.SnapEditor.{SnapCodeEditorConfig, LibraryTab}

/** Configuration for the EvaEditor, which combines multiple language editors.
  *
  * @param snapConfig the Snap editor configuration (always required)
  * @param enabledLanguages list of programming languages to enable (tabs will be created for these)
  * @param additionalLibraryTabs additional custom library tabs to display in the Snap editor
  */
case class EvaEditorConfig(
    snapConfig: SnapCodeEditorConfig = SnapCodeEditorConfig(),
    enabledLanguages: List[ProgrammingLanguage] = List(Python, Java),
    additionalLibraryTabs: List[LibraryTab] = Nil
)

object EvaEditorConfig {
  val Default: EvaEditorConfig = EvaEditorConfig()
}
