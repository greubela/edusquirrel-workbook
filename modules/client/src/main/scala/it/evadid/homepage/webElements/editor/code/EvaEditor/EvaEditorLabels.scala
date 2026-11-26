package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.editor.code.JavaEditor.JavaEditorLabels
import it.evadid.homepage.webElements.editor.code.program.{ProgrammingExecutionLabels, SourceWarningLabels}
import it.evadid.homepage.webElements.editor.blocks.BlockEditorLabels

/** UI strings for Eva; supply via [[fromContent]] from a workbook renderer. */
final case class EvaEditorLabels(
    unavailable: Signal[String],
    languages: Signal[String],
    switchFailed: Signal[String],
    loading: Signal[String],
    conversion: Signal[EvaConversionTexts],
    execution: ProgrammingExecutionLabels,
    sourceWarnings: SourceWarningLabels,
    blocks: BlockEditorLabels,
    python: it.evadid.homepage.webElements.editor.code.PythonEditor.PythonEditorLabels,
    java: JavaEditorLabels
)

object EvaEditorLabels {
  def fromContent(text: LanguageMapContentId => Signal[String]): EvaEditorLabels = EvaEditorLabels(
    unavailable = text(LanguageMapContentId("blockeditor/unavailable")),
    languages = text(LanguageMapContentId("blockeditor/languages")),
    switchFailed = text(LanguageMapContentId("blockeditor/switchFailed")),
    loading = text(LanguageMapContentId("blockeditor/loading")),
    conversion = Signal.combine(text(LanguageMapContentId("blockeditor/conversionUnparsable")),
      text(LanguageMapContentId("blockeditor/conversionUnsupported")), text(LanguageMapContentId("blockeditor/conversionLine")),
      text(LanguageMapContentId("blockeditor/conversionWarnings"))).map(EvaConversionTexts.apply),
    execution = ProgrammingExecutionLabels.fromContent(text),
    sourceWarnings = SourceWarningLabels(key => text(LanguageMapContentId("blockeditor", key))),
    blocks = BlockEditorLabels.fromContent(text),
    python = new it.evadid.homepage.webElements.editor.code.PythonEditor.PythonEditorLabels(text),
    java = JavaEditorLabels.fromContent(text)
  )
}
