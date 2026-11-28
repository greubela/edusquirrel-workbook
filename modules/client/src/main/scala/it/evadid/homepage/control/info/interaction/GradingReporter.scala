package it.evadid.homepage.control.info.interaction

import it.evadid.workbook.interaction.state.InteractionSignals.*

import it.evadid.workbook.interaction.state.InteractionController

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.UpdateImportance.DEFAULT
import it.evadid.workbook.meta.{CheckReport, GradingSummary, ScaffoldingReport}

/** One shared snapshot per history update, independent of the number of mounted views. */
final class GradingReporter private (
    val isGradingSupported: Boolean,
    val isScaffoldingSupported: Boolean,
    val report: Signal[GradingReporter.Report],
    val disabled: Signal[Boolean]
) {
  val summary = report.map(_.grading)
  val scaffolding = report.map(_.scaffolding)
  val checks = report.map(_.checks)
}

object GradingReporter {
  final case class Report(grading: GradingSummary, scaffolding: ScaffoldingReport, interacted: Boolean, checks: CheckReport)

  /** Capture F here; views receive format-independent summaries without casts. */
  def apply[F <: InteractionFormat](interaction: WorkbookInteractionElement[F], interactions: InteractionController): GradingReporter = {
    val report = interactions.historySignal(interaction).map { history =>
      val state = history.historyStates.lastStateOption.get.value
      val feedback = interactions.feedback(interaction)
      Report(
        GradingSummary(interaction.gradingElements, feedback.getCurrentStateOfGrading(state)),
        ScaffoldingReport(feedback.getCurrentStateOfScaffolding(state)),
        history.historyStates.lastStateOption.exists(_.updateImportance != DEFAULT),
        CheckReport(interaction, history))
    }
    new GradingReporter(interaction.gradingElements.nonEmpty, interaction.scaffoldingElement.nonEmpty,
      report, interaction.isDisabledState.observable.toSignal(interaction.isDisabledState.now()))
  }
}
