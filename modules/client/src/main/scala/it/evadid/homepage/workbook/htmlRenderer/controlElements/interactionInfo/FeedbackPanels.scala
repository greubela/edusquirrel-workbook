package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.BooleanAsIsCodec
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.FeedbackPresentation.*
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.{FeedbackIcon, createFeedbackIcon}
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.meta.{GradingSummary, ScaffoldingReport}
import java.time.format.DateTimeFormatter

/**
 * Grading and hint presentation shared by the workbook overview and the fullscreen editor sidebar.
 * Both placements render the same parts from one [[InteractionFeedbackView]] (and thus one
 * `GradingReporter`); they differ only in how the results are disclosed and when the hint card shows.
 */
object FeedbackPanels {
  enum Placement(val css: String) {
    /** Below an exercise: only the request buttons show until the reader expands the results; hint card once a hint exists. */
    case Overview extends Placement("overview")
    /** Beside the fullscreen editor: results expanded, hint card always present. */
    case Sidebar extends Placement("sidebar")
  }

  private val dateFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
  private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
  private val openProp = htmlProp("open", BooleanAsIsCodec)

  /** Action bar, grading results and hint card for one interaction. */
  private[interactionInfo] def panel[F <: InteractionFormat](view: InteractionFeedbackView[F], placement: Placement): Seq[Element] =
    if placement == Placement.Overview && view.interaction.gradingElements.nonEmpty then collapsedOverview(view)
    else Seq(div(cls := barCss, actions(view, None), statusLine(view))) ++ gradingCard(view, placement).toSeq ++ hintCard(view, placement).toSeq

  private val barCss = "interaction-feedback feedback-bar grading-info-line"

  /**
   * Overview of a graded exercise: the status line and results stay hidden until the reader opens them
   * with the details toggle; arriving results never expand them.
   */
  private def collapsedOverview[F <: InteractionFormat](view: InteractionFeedbackView[F]): Seq[Element] = {
    val expanded = Var(false)
    val toggle = button(typ := "button", cls := "feedback-action feedback-details-toggle",
      aria.expanded <-- expanded.signal,
      span(cls := "feedback-button-label", child.text <-- expanded.signal.flatMapSwitch(open =>
        view.label(if open then "gradingDetailsHide" else "gradingDetailsShow"))),
      onClick --> (_ => expanded.update(!_)))
    Seq(div(cls := barCss, actions(view, Some(toggle))),
      div(cls := "feedback-details", hidden <-- expanded.signal.map(!_),
        statusLine(view), gradingCard(view, Placement.Overview))) ++ hintCard(view, Placement.Overview).toSeq
  }

  private def tone(tone: Signal[Tone], base: String): Modifier[HtmlElement] =
    cls <-- tone.map(value => s"$base feedback-status--${value.css}")

  private def toneIcon(value: Signal[Tone]): Element =
    span(cls := "feedback-icon-slot", child <-- value.distinct.map(t => createFeedbackIcon(t.icon)))

  /** Request buttons, optionally followed by the overview's details toggle. */
  private def actions[F <: InteractionFormat](view: InteractionFeedbackView[F], toggle: Option[Element]): Element = {
    val canGrade = view.interaction.gradingElements.nonEmpty
    val canHint = view.interaction.scaffoldingElement.nonEmpty
    div(cls := "feedback-actions",
      Option.when(canGrade)(button(typ := "button", cls := "feedback-action feedback-action--primary",
        title <-- view.label("gradingRequest"),
        createFeedbackIcon(FeedbackIcon.Check), span(cls := "feedback-button-label", child.text <-- view.label("gradingRequest")),
        disabled <-- view.grading.combineWith(view.disabled).map((grading, disabled) => disabled || grading.pending),
        onClick --> (_ => view.requestGrading()))),
      Option.when(canHint)(button(typ := "button", cls := "feedback-action scaffolding-info",
        title <-- view.label("scaffoldingRequest"),
        createFeedbackIcon(FeedbackIcon.Hint), span(cls := "feedback-button-label", child.text <-- view.label("scaffoldingRequest")),
        disabled <-- view.scaffolding.combineWith(view.disabled).map((result, disabled) =>
          disabled || result.status == ScaffoldingReport.Status.Pending),
        dataAttr("tooltip") <-- view.scaffolding.flatMapSwitch(view.scaffoldingText),
        onClick --> (_ => view.requestScaffolding()))),
      toggle)
  }

  /** Overall result with progress, and the hint state. */
  private def statusLine[F <: InteractionFormat](view: InteractionFeedbackView[F]): Element = {
    val canGrade = view.interaction.gradingElements.nonEmpty
    val canHint = view.interaction.scaffoldingElement.nonEmpty
    val scoreText = view.grading.combineWith(view.label("gradingCompactScore")).map { (state, template) =>
      template.replace("{correct}", state.correct.toString).replace("{total}", state.total.toString)
    }
    val hintStatus = view.scaffolding.flatMapSwitch(result => view.label(view.scaffoldingLabel(result)))
      .combineWith(view.label("scaffoldingLabel")).map((status, label) => s"$label: $status")
    div(cls := "feedback-status-line", role := "status", aria.live := "polite",
      Option.when(canGrade)(span(tone(view.grading.map(_.tone), "feedback-chip feedback-score-chip"),
        title <-- scoreText, aria.label <-- scoreText,
        toneIcon(view.grading.map(_.tone)),
        span(cls := "feedback-chip__text", child.text <-- view.grading.flatMapSwitch { state =>
          if state.notRequested == state.total then view.label("gradingNotRun") else Val(s"${state.correct}/${state.total}")
        }),
        progressTag(cls := "feedback-progress", aria.hidden := true,
          maxAttr <-- view.grading.map(_.total.max(1).toString), value <-- view.grading.map(_.correct.toString)))),
      Option.when(canGrade)(child.maybe <-- view.grading.combineWith(view.label("gradingCompactUnavailable")).map { (state, template) =>
        Option.when(state.unavailable > 0 && state.unavailable < state.total) {
          val text = template.replace("{count}", state.unavailable.toString)
          span(cls := "feedback-chip feedback-status--neutral", title := text, aria.label := text,
            createFeedbackIcon(FeedbackIcon.NotRun), span(cls := "feedback-chip__text", state.unavailable.toString))
        }
      }),
      Option.when(canHint)(span(tone(view.scaffolding.map(_.status.tone), "feedback-chip feedback-hint-chip"),
        title <-- hintStatus, aria.label <-- hintStatus,
        createFeedbackIcon(FeedbackIcon.Hint),
        span(cls := "feedback-chip__text", child.text <-- view.scaffolding.flatMapSwitch(result => view.label(view.scaffoldingLabel(result)))))))
  }

  /** Counts per nonempty result tone, shown in the header so a collapsed section stays informative. */
  private def groupChips[F <: InteractionFormat](view: InteractionFeedbackView[F]): Element =
    span(cls := "feedback-group-chips", children <-- view.grading.map { state =>
      state.toneCounts.toList.map { (resultTone, count) =>
        val text = view.label(resultTone.labelKey).map(label => s"$label: $count")
        span(cls := s"feedback-chip feedback-chip--compact feedback-status--${resultTone.css}",
          title <-- text, aria.label <-- text,
          createFeedbackIcon(resultTone.icon), span(cls := "feedback-chip__text", count.toString))
      }
    })

  private def entryRow[F <: InteractionFormat](view: InteractionFeedbackView[F], entry: GradingSummary.Entry): Element = {
    val time = entry.result.filter(_ => entry.executed && !entry.hidden).map(_.gradingTime)
    li(cls := s"feedback-entry feedback-status--${entry.tone.css}",
      span(cls := "feedback-entry__icon", createFeedbackIcon(if entry.hidden then FeedbackIcon.Hidden else entry.tone.icon)),
      div(cls := "feedback-entry__body",
        div(cls := "feedback-entry__heading",
          span(cls := "feedback-test-description", child.text <-- view.title(entry)),
          span(cls := "feedback-entry__meta",
            span(cls := "feedback-entry__status", child.text <-- view.label(entry.statusKey)),
            time.map(at => timeTag(cls := "feedback-date", htmlAttr("datetime", com.raquo.laminar.codecs.StringAsIsCodec) := at.toString,
              title := at.format(dateFormat), at.format(timeFormat))))),
        view.feedback(entry).map(text => p(cls := "feedback-text", child.text <-- text))))
  }

  /** Expanded in the sidebar; in the overview a plain section, as the details toggle already discloses it. */
  private def gradingCard[F <: InteractionFormat](view: InteractionFeedbackView[F], placement: Placement): Option[Element] =
    Option.when(view.interaction.gradingElements.nonEmpty) {
      val sectionCss = s"feedback-section feedback-grading feedback-section--${placement.css}"
      val header = Seq(
        span(cls := "feedback-summary__icon", createFeedbackIcon(FeedbackIcon.Grading)),
        h3(cls := "feedback-summary__title", child.text <-- view.label("SidepanelGradingTitle")),
        groupChips(view))
      val entries = ul(cls := "feedback-entries feedback-section-content",
        children <-- view.grading.map(_.entries.map(entryRow(view, _))))
      placement match {
        case Placement.Sidebar => detailsTag(cls := sectionCss, openProp := true, summaryTag(cls := "feedback-summary", header), entries)
        case Placement.Overview => sectionTag(cls := sectionCss, div(cls := "feedback-summary feedback-summary--static", header), entries)
      }
    }

  /**
   * In the overview the card appears only for a completed hint on the current answer; in the
   * sidebar it always explains the hint state. It opens automatically whenever content arrives.
   */
  private def hintCard[F <: InteractionFormat](view: InteractionFeedbackView[F], placement: Placement): Option[Element] =
    Option.when(view.interaction.scaffoldingElement.nonEmpty) {
      val ready = view.scaffolding.map(_.status == ScaffoldingReport.Status.Ready).distinct
      val requested = view.scaffolding.map(_.status != ScaffoldingReport.Status.NotRequested).distinct
      detailsTag(cls := s"feedback-section interaction-hint-panel feedback-section--${placement.css}",
        if placement == Placement.Overview then hidden <-- ready.map(!_) else emptyMod,
        openProp <-- (if placement == Placement.Overview then ready else requested),
        summaryTag(cls := "feedback-summary",
          span(cls := "feedback-summary__icon", createFeedbackIcon(FeedbackIcon.Hint)),
          h3(cls := "feedback-summary__title", child.text <-- view.label("SidepanelScaffoldingTitle")),
          span(tone(view.scaffolding.map(_.status.tone), "feedback-chip feedback-chip--compact"),
            toneIcon(view.scaffolding.map(_.status.tone)),
            span(cls := "feedback-chip__text", child.text <-- view.scaffolding.flatMapSwitch(result => view.label(view.scaffoldingLabel(result)))))),
        p(cls := "feedback-text feedback-section-content feedback-hint-text",
          child.text <-- (if placement == Placement.Overview
            then view.scaffolding.flatMapSwitch(result => result.feedback.map(view.localized).getOrElse(Val("")))
            else view.scaffolding.flatMapSwitch(view.scaffoldingText))))
    }
}
