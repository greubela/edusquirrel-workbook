package it.evadid.homepage.webElements.editor.config

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.editor.abstractions.WebEditorConfig;


case class TextEditorConfig(monospace: Boolean, rowsCount: Int, colsCount: Int, placeholder: LanguageMapContentId, protected val configCssClasses: List[String]) extends WebEditorConfig {

  protected val cssMonoStr: String = if (monospace) " mono" else ""

  protected override def additionalCssClasses: List[String] = configCssClasses ++ List(cssMonoStr)
}

object TextEditorConfig {

  val defaultConfig: TextEditorConfig =

    TextEditorConfig(
      monospace = false,
      rowsCount = 4,
      colsCount = 110,
      placeholder = LanguageMapContentId("basic/textEditorPlaceholder"),
      configCssClasses = List()
    )
}