package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.rules

/**
 * Severity level of a rule violation.
 * Can be used later for scoring/aggregation.
 */
sealed trait RuleSeverity {
  def weight: Int
}

object RuleSeverity {
  case object Info extends RuleSeverity    { val weight: Int = 1 }
  case object Warning extends RuleSeverity { val weight: Int = 3 }
  case object Error extends RuleSeverity   { val weight: Int = 5 }
}

/**
 * Unified result format for static rules
 * (applies to both VM and Python levels).
 */
final case class RuleResult(
  id: String,
  category: String,
  severity: RuleSeverity,
  passed: Boolean,
  message: String,
  details: Option[String] = None
)
