package it.evadid.vm.conversion.structured

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.conversion.ConversionInfo
import it.evadid.util.logging.LoggingLevel

/** One source preparation shared by parsing and conversion diagnostics: classes hoisted before module code. */
case class PreparedProgramSource(code: String, diagnostics: Seq[ConversionInfo])
object PreparedProgramSource {
  def apply(code: String, language: ProgrammingLanguage): PreparedProgramSource = {
    val warnings = Vector.newBuilder[ConversionInfo]
    def warn(message: String): Unit = {
      it.evadid.util.logging.Logger.withNameAndPrefixes(Some("ProgrammingConversions")).logWarn(message)
      warnings += ConversionInfo(message, LoggingLevel.WARN, true)
    }
    val parts = language match {
      case Python => StructuredProgramConversions.pythonParts(code, warn)
      case Java => StructuredProgramConversions.javaParts(code, warn)
      case _ => return PreparedProgramSource(code, Seq.empty)
    }
    PreparedProgramSource((parts.definedClasses.values.toSeq :+ parts.remaining).filter(_.nonEmpty).mkString("\n\n"), warnings.result())
  }
}
