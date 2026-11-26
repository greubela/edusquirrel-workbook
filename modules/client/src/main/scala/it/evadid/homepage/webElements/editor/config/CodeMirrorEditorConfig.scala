package it.evadid.homepage.webElements.editor.config

import it.evadid.core.datastructures.font.AppFont
import it.evadid.homepage.webElements.editor.abstractions.WebEditorConfig

/** The flowchart is available for Python programs accepted by the shared parser. */
case class CodeMirrorEditorConfig(showFlowchartGutter: Boolean = false, editorFont: AppFont = AppFont("JetBrains Mono", 14))
    extends WebEditorConfig {
  override protected def additionalCssClasses: List[String] = List("code-editor")
}

object CodeMirrorEditorConfig {
  val Default: CodeMirrorEditorConfig = CodeMirrorEditorConfig()
  val Flowchart: CodeMirrorEditorConfig = CodeMirrorEditorConfig(showFlowchartGutter = true)
}
