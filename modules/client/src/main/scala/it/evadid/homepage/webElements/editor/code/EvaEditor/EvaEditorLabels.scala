package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.editor.blocks.BlockEditorLabels
import it.evadid.homepage.webElements.code.JavaEditorLabels

/** UI strings for Eva; supply via [[fromContent]] from a workbook renderer. */
final case class EvaEditorLabels(
    unavailable: Signal[String],
    languages: Signal[String],
    switchFailed: Signal[String],
    loading: Signal[String],
    blocks: BlockEditorLabels,
    java: JavaEditorLabels = JavaEditorLabels.keys,
    python: JavaEditorLabels = JavaEditorLabels.keys
)

object EvaEditorLabels {
  def fromContent(text: LanguageMapContentId => Signal[String]): EvaEditorLabels = EvaEditorLabels(
    unavailable = text(LanguageMapContentId("basic/editorUnavailable")),
    languages = text(LanguageMapContentId("basic/editorLanguages")),
    switchFailed = text(LanguageMapContentId("basic/editorSwitchFailed")),
    loading = text(LanguageMapContentId("basic/editorLoading")),
    blocks = BlockEditorLabels.fromContent(text),
    java = JavaEditorLabels.fromContent(text),
    python = JavaEditorLabels.fromContent(text, python = true)
  )
}
