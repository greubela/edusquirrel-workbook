package it.evadid.homepage.webElements.editor.code.PythonEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId

final class PythonEditorLabels(text: LanguageMapContentId => Signal[String]) {
  def apply(key: String): Signal[String] = text(LanguageMapContentId("blockeditor", key))
}
