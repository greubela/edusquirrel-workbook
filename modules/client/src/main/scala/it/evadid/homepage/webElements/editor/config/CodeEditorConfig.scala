package it.evadid.homepage.webElements.editor.config

import it.evadid.homepage.webElements.editor.abstractions.WebEditorConfig

case class CodeEditorConfig(
  editorFont: String = "JetBrains Mono",
  fontSize: Int = 14,
  placeholder: String = ""
) extends WebEditorConfig {

  protected def additionalCssClasses: List[String] = List("code-editor")

}

object CodeEditorConfig {

  val defaultConfig: CodeEditorConfig = CodeEditorConfig()

}
