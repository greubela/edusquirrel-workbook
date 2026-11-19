package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import it.evadid.workbook.meta.{GradingSummary, ScaffoldingReport}

/** Labels and CSS belong to the browser; status decisions belong to reporters. */
private[interactionInfo] object FeedbackPresentation {
  extension (group: GradingSummary.Group) {
    def labelKey: String = group match {
      case GradingSummary.Group.Correct => "gradingGroupCorrect"
      case GradingSummary.Group.Incorrect => "gradingGroupIncorrect"
      case GradingSummary.Group.Pending => "gradingGroupPending"
      case GradingSummary.Group.Error => "gradingGroupError"
      case GradingSummary.Group.NotRequested => "gradingGroupNotRequested"
    }
    def tone: String = group match {
      case GradingSummary.Group.Correct => "correct"
      case GradingSummary.Group.Incorrect => "incorrect"
      case GradingSummary.Group.Pending => "pending"
      case GradingSummary.Group.Error => "error"
      case GradingSummary.Group.NotRequested => "neutral"
    }
  }
  extension (status: ScaffoldingReport.Status) {
    def labelKey: String = status match {
      case ScaffoldingReport.Status.NotRequested => "gradingGroupNotRequested"
      case ScaffoldingReport.Status.Pending => "gradingGroupPending"
      case ScaffoldingReport.Status.Error => "gradingGroupError"
      case ScaffoldingReport.Status.Ready => "scaffoldingReady"
    }
  }
}
