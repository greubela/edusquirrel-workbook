package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId

sealed trait AdvancedCodeCheckResult

object AdvancedCodeCheckResult {

  case object Success extends AdvancedCodeCheckResult

  case class Incomplete(missingHints: Seq[LanguageMapContentId]) extends AdvancedCodeCheckResult

  def evaluate(code: String, requirements: Seq[AdvancedCodeRequirement]): AdvancedCodeCheckResult = {
    val missing = requirements.collect {
      case req if !req.mustContain.forall(fragment => code.contains(fragment)) => req.missingHint
    }
    if (missing.isEmpty) Success else Incomplete(missing)
  }

}
