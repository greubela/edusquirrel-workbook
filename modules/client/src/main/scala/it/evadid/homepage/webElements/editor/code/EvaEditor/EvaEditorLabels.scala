package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId

/** Texts supplied by the renderer. English defaults keep the editor usable without a workbook. */
final case class EvaEditorLabels(
    unavailable: Signal[String] = Val("This draft is not available in the configured languages."),
    languages: Signal[String] = Val("Programming languages"),
    switchFailed: Signal[String] = Val("Could not switch language"),
    loading: Signal[String] = Val("Loading editor…")
)

object EvaEditorLabels {
  def fromContent(text: LanguageMapContentId => Signal[String]): EvaEditorLabels = EvaEditorLabels(
    unavailable = text(LanguageMapContentId("basic/editorUnavailable")),
    languages = text(LanguageMapContentId("basic/editorLanguages")),
    switchFailed = text(LanguageMapContentId("basic/editorSwitchFailed")),
    loading = text(LanguageMapContentId("basic/editorLoading"))
  )
}
