package it.evadid.vm.code.errors

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.vm.code.abstractions.BeExpression

/** Source text is preserved only where its syntax is meaningful. */
trait BeSourcePlaceholder extends BeExpression {
  def originalSource: String
  def parsedFromLanguage: Option[ProgrammingLanguage]
  def sourceFor(language: ProgrammingLanguage): String = {
    require(parsedFromLanguage.forall(_ == language),
      s"Cannot emit preserved ${parsedFromLanguage.map(_.toString).getOrElse("unknown")} source as $language.")
    originalSource
  }
}
