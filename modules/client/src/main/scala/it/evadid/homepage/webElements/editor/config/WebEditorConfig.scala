package it.evadid.homepage.webElements.editor.config

case class WebEditorConfig(override protected val additionalCssClasses: List[String] = Nil)
  extends it.evadid.homepage.webElements.editor.abstractions.WebEditorConfig

object WebEditorConfig {
  val defaultConfig: WebEditorConfig = WebEditorConfig()
}
