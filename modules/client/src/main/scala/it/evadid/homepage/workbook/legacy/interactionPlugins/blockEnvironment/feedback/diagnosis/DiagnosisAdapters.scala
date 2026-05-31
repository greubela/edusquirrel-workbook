package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.diagnosis

import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ml.{BlockFeedbackSignals, DecisionLayer}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.BlockFeedbackRequest
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.service.BlockFeedbackTestPlan

/**
 * Optional per-exercise adapters.
 *
 * The default pipeline MUST work without adapters (future tasks).
 * Adapters can add extra tags/evidence/next checks when we recognize a known task.
 */
object DiagnosisAdapters:

  trait Adapter:
    def augment(
      diagnosis: Diagnosis,
      request: BlockFeedbackRequest,
      plan: BlockFeedbackTestPlan,
      signals: BlockFeedbackSignals,
      decision: DecisionLayer.Decision
    ): Diagnosis

  private object NoopAdapter extends Adapter:
    override def augment(
      diagnosis: Diagnosis,
      request: BlockFeedbackRequest,
      plan: BlockFeedbackTestPlan,
      signals: BlockFeedbackSignals,
      decision: DecisionLayer.Decision
    ): Diagnosis = diagnosis

  private object KnownExercisesAdapter extends Adapter:
    override def augment(
      diagnosis: Diagnosis,
      request: BlockFeedbackRequest,
      plan: BlockFeedbackTestPlan,
      signals: BlockFeedbackSignals,
      decision: DecisionLayer.Decision
    ): Diagnosis =
      request.meta.exerciseId match
        case Some(id) if id.toLowerCase.contains("balanced") || id.toLowerCase.contains("bracket") =>
          diagnosis.copy(taskProfile = diagnosis.taskProfile.copy(tags = (diagnosis.taskProfile.tags :+ "stack").distinct))
        case Some(id) if id.toLowerCase.contains("two") && id.toLowerCase.contains("sum") =>
          diagnosis.copy(taskProfile = diagnosis.taskProfile.copy(tags = (diagnosis.taskProfile.tags :+ "hashmap").distinct))
        case Some(id) if id.toLowerCase.contains("max") && id.toLowerCase.contains("list") =>
          diagnosis.copy(taskProfile = diagnosis.taskProfile.copy(tags = (diagnosis.taskProfile.tags :+ "aggregation").distinct))
        case Some(id) if id.toLowerCase.contains("add") && id.toLowerCase.contains("number") =>
          diagnosis.copy(taskProfile = diagnosis.taskProfile.copy(tags = (diagnosis.taskProfile.tags :+ "arithmetic").distinct))
        case _ => diagnosis

  def applyAdapters(
    diagnosis: Diagnosis,
    request: BlockFeedbackRequest,
    plan: BlockFeedbackTestPlan,
    signals: BlockFeedbackSignals,
    decision: DecisionLayer.Decision
  ): Diagnosis =
    val adapter: Adapter =
      request.meta.exerciseId match
        case Some(_) => KnownExercisesAdapter
        case None => NoopAdapter

    adapter.augment(diagnosis, request, plan, signals, decision)
