package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.code.JavaEditorLabels
import it.evadid.homepage.webElements.editor.code.program.ProgrammingExecutionLabels
import it.evadid.homepage.webElements.editor.blocks.BlockEditorLabels

/** UI strings for Eva; supply via [[fromContent]] from a workbook renderer. */
final case class EvaEditorLabels(
    unavailable: Signal[String],
    languages: Signal[String],
    switchFailed: Signal[String],
    loading: Signal[String],
    execution: ProgrammingExecutionLabels,
    blocks: BlockEditorLabels,
    java: JavaEditorLabels
)

object EvaEditorLabels {
  def fromContent(text: LanguageMapContentId => Signal[String]): EvaEditorLabels = EvaEditorLabels(
    unavailable = text(LanguageMapContentId("blockeditor/unavailable")),
    languages = text(LanguageMapContentId("blockeditor/languages")),
    switchFailed = text(LanguageMapContentId("blockeditor/switchFailed")),
    loading = text(LanguageMapContentId("blockeditor/loading")),
    execution = ProgrammingExecutionLabels.fromContent(text),
    blocks = BlockEditorLabels.fromContent(text),
    java = JavaEditorLabels.fromContent(text)
  )
}
