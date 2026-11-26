package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.util.logging.LoggingLevel
import it.evadid.vm.code.errors.{ProgrammingConversionError, ProgrammingConversionKind}
import it.evadid.vm.conversion.ConversionResult

/** Why a language switch failed: the typed cause, shown localized, and the parser's technical `detail`. */
final case class EvaConversionFailure(error: ProgrammingConversionError, detail: String)

object EvaConversionFailure {
  def of(result: ConversionResult): Option[EvaConversionFailure] =
    Option.when(result.finalState.isEmpty) {
      val info = result.conversionInfo.find(_.warningLevel == LoggingLevel.ERROR)
      EvaConversionFailure(info.flatMap(_.sourceError).getOrElse(ProgrammingConversionError(ProgrammingConversionKind.UnsupportedConstruct)),
        info.map(_.warningMsg).getOrElse(""))
    }
}
