package interactionPlugins.blockEnvironment.feedback.diagnosis

import interactionPlugins.blockEnvironment.feedback.ml.DecisionLayer

/**
 * Structured diagnosis that is stable across exercises.
 *
 * The LLM is only allowed to write student-facing text based on this object
 * (plus the exercise statement + a short code excerpt for context).
 */
final case class Diagnosis(
  category: DiagnosisCategory,
  primaryIssue: DecisionLayer.IssueType,
  secondaryIssues: Seq[DecisionLayer.IssueType],
  severity: DecisionLayer.Severity,
  confidence: Double,
  hypotheses: Seq[DiagnosisHypothesis],
  evidence: Seq[EvidenceItem],
  taskProfile: TaskProfile,
  recommendedNextChecks: Seq[String]
)

enum DiagnosisCategory:
  case Syntax
  case Runtime
  case Spec
  case Logic
  case Performance
  case Unknown

final case class DiagnosisHypothesis(
  issue: DecisionLayer.IssueType,
  confidence: Double,
  rationale: String
)

/**
 * Evidence is intentionally "small" and referenceable.
 *
 * - `id` should be stable for joining with UI / logs.
 * - `kind` helps keep the LLM grounded (tests vs runtime vs static).
 */
final case class EvidenceItem(
  id: String,
  kind: EvidenceKind,
  title: String,
  details: String
)

enum EvidenceKind:
  case VisibleTest
  case HiddenTest
  case RuntimeError
  case Stdout
  case Stderr
  case StaticRule
  case VmRule
  case Signal
  case Heuristic
  case Meta

/**
 * Generic profile inferred from statement + tests.
 * Meant to adapt to future exercises.
 */
final case class TaskProfile(
  tags: Seq[String],
  requiresPureFunction: Boolean,
  ioLikelyForbidden: Boolean,
  expectsReturnValue: Boolean,
  outputFormatSensitive: Boolean
)

object TaskProfile:
  val Empty: TaskProfile =
    TaskProfile(
      tags = Nil,
      requiresPureFunction = false,
      ioLikelyForbidden = false,
      expectsReturnValue = true,
      outputFormatSensitive = false
    )
