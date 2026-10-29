package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId

/** UI strings for Eva; supply via [[fromContent]] from a workbook renderer. */
final case class EvaEditorLabels(
    unavailable: Signal[String],
    languages: Signal[String],
    switchFailed: Signal[String],
    loading: Signal[String]
)

object EvaEditorLabels {
  def fromContent(text: LanguageMapContentId => Signal[String]): EvaEditorLabels = EvaEditorLabels(
    unavailable = text(LanguageMapContentId("basic/editorUnavailable")),
    languages = text(LanguageMapContentId("basic/editorLanguages")),
    switchFailed = text(LanguageMapContentId("basic/editorSwitchFailed")),
    loading = text(LanguageMapContentId("basic/editorLoading"))
  )
}
