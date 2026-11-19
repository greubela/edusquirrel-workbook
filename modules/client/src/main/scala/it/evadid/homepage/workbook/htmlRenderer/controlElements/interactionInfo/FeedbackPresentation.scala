package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.FeedbackIcon
import it.evadid.workbook.abstractions.grading.GradingStatus
import it.evadid.workbook.meta.{GradingSummary, ScaffoldingReport}

/** Labels, tones and icons belong to the browser; status decisions belong to reporters. */
private[interactionInfo] object FeedbackPresentation {
  /** Visual tone shared by status chips, result rows and the CSS modifier `feedback-status--<tone>`. */
  enum Tone(val css: String, val icon: FeedbackIcon, val labelKey: String) {
    case Correct extends Tone("correct", FeedbackIcon.Passed, "gradingGroupCorrect")
    case Partial extends Tone("partial", FeedbackIcon.Partial, "gradingPartiallyCorrect")
    case Incorrect extends Tone("incorrect", FeedbackIcon.Failed, "gradingGroupIncorrect")
    case Pending extends Tone("pending", FeedbackIcon.Pending, "gradingGroupPending")
    case Error extends Tone("error", FeedbackIcon.Error, "gradingGroupError")
    case Neutral extends Tone("neutral", FeedbackIcon.NotRun, "gradingGroupNotRequested")
  }

  extension (group: GradingSummary.Group) {
    def tone: Tone = group match {
      case GradingSummary.Group.Correct => Tone.Correct
      case GradingSummary.Group.Incorrect => Tone.Incorrect
      case GradingSummary.Group.Pending => Tone.Pending
      case GradingSummary.Group.Error => Tone.Error
      case GradingSummary.Group.NotRequested => Tone.Neutral
    }
  }

  extension (entry: GradingSummary.Entry) {
    private def partial: Boolean = entry.result.exists(_.gradingStatus == GradingStatus.PartiallyCorrect())
    /** Partial results stay in the incorrect group but keep their own label and icon. */
    def tone: Tone = if partial then Tone.Partial else entry.group.tone
    /** Student-facing status word of one result row. */
    def statusKey: String = if partial then "gradingPartiallyCorrect" else entry.group match {
      case GradingSummary.Group.Correct => "gradingGroupCorrect"
      case GradingSummary.Group.Incorrect => "gradingGroupIncorrect"
      case GradingSummary.Group.Pending => "gradingLoading"
      case GradingSummary.Group.Error => "gradingError"
      case GradingSummary.Group.NotRequested => "gradingNotRun"
    }
  }

  extension (summary: GradingSummary) {
    /** Row tones in display order with their counts; partial results are counted separately. */
    def toneCounts: Seq[(Tone, Int)] = {
      val counts = summary.entries.groupMapReduce(_.tone)(_ => 1)(_ + _)
      Tone.values.toSeq.flatMap(tone => counts.get(tone).map(tone -> _))
    }
    def tone: Tone = summary.status match {
      case GradingStatus.Correct() => Tone.Correct
      case GradingStatus.PartiallyCorrect() => Tone.Partial
      // No fully correct result, but partial credit: not shown as a plain failure.
      case GradingStatus.Incorrect() if summary.entries.exists(_.tone == Tone.Partial) => Tone.Partial
      case GradingStatus.Incorrect() => Tone.Incorrect
      case GradingStatus.NotFinished() => Tone.Pending
      case GradingStatus.TestError(_) => Tone.Error
      case GradingStatus.NotRequested() => Tone.Neutral
    }
  }

  extension (status: ScaffoldingReport.Status) {
    def labelKey: String = status match {
      case ScaffoldingReport.Status.NotRequested => "gradingGroupNotRequested"
      case ScaffoldingReport.Status.Pending => "gradingGroupPending"
      case ScaffoldingReport.Status.Error => "gradingGroupError"
      case ScaffoldingReport.Status.Ready => "scaffoldingReady"
    }
    /** Explanatory text shown while no hint content exists. */
    def messageKey: String = status match {
      case ScaffoldingReport.Status.NotRequested => "requestScaffoldingHint"
      case ScaffoldingReport.Status.Pending => "scaffoldingLoading"
      case ScaffoldingReport.Status.Error => "scaffoldingError"
      case ScaffoldingReport.Status.Ready => "scaffoldingReady"
    }
    def tone: Tone = status match {
      case ScaffoldingReport.Status.NotRequested => Tone.Neutral
      case ScaffoldingReport.Status.Pending => Tone.Pending
      case ScaffoldingReport.Status.Error => Tone.Error
      case ScaffoldingReport.Status.Ready => Tone.Correct
    }
  }
}
