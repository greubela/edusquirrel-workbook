package it.evadid.homepage.webElements.editor.config

import it.evadid.homepage.webElements.editor.abstractions.WebEditorConfig

/** The flowchart is available for Python programs accepted by the shared parser. */
case class CodeMirrorEditorConfig(showFlowchartGutter: Boolean = false)
    extends WebEditorConfig derives upickle.default.ReadWriter {
  override protected def additionalCssClasses: List[String] = List("code-editor")
}

object CodeMirrorEditorConfig {
  val Default: CodeMirrorEditorConfig = CodeMirrorEditorConfig()
  val Flowchart: CodeMirrorEditorConfig = CodeMirrorEditorConfig(showFlowchartGutter = true)
}
