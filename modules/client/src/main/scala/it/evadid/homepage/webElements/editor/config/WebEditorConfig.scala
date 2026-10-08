package it.evadid.homepage.webElements.editor.config

import com.raquo.laminar.api.L.*

case class WebEditorConfig(
  inputCssClassStr: List[String] => String = (classes: List[String]) => classes.mkString(" ")
)

object WebEditorConfig {
  val defaultConfig: WebEditorConfig = WebEditorConfig()
}
