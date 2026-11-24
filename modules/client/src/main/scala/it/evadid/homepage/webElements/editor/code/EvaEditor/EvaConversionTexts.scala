package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.vm.code.errors.ProgrammingConversionKind

/** The localized conversion texts of one display language, resolved together by [[EvaEditorLabels]]. */
final case class EvaConversionTexts(unparsable: String, unsupported: String, line: String, warnings: String) {
  /** E.g. "This construct cannot be converted (line 3): while-else". */
  def describe(failure: EvaConversionFailure): String = {
    val summary = failure.error.kind match {
      case ProgrammingConversionKind.Unparsable => unparsable
      case ProgrammingConversionKind.UnsupportedConstruct => unsupported
    }
    val located = failure.error.line.fold(summary)(number => s"$summary (${line.replace("{line}", number.toString)})")
    if failure.detail.isEmpty then located else s"$located: ${failure.detail}"
  }
}
