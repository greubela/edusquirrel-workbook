package it.evadid.homepage.webElements.editor.config

import it.evadid.homepage.webElements.editor.abstractions.WebEditorConfig

case class SelectorEditorConfig() extends WebEditorConfig derives upickle.default.ReadWriter {

  protected def additionalCssClasses: List[String] = List("selector-editor")

}

object SelectorEditorConfig {

  val defaultConfig: SelectorEditorConfig = SelectorEditorConfig()

}
