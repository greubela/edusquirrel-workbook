package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay.*
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.reports.{GradingSummary, ScaffoldingReport}
import it.evadid.homepage.control.info.interaction.GradingReporter


case class IconGradingAndScaffolding[F <: InteractionFormat](
                                      fullInfo: FullInfo,
                                      laminarHelper: LaminarRenderHelper,
                                      workbookInteractionElement: WorkbookInteractionElement[F],
                                      gradingReporter: GradingReporter) {



  def deriveScaffoldingSymbol(report: ScaffoldingReport): Element = {
    import ScaffoldingReport.Status
    if !gradingReporter.isScaffoldingSupported then div()
    else {
      val icon = report.status match {
        case Status.NotRequested => createScaffoldingNothing()
        case Status.Pending => createScaffoldingLoading()
        case Status.Error => createScaffoldingError()
        case Status.Ready => createScaffoldingFinished()
      }
      val tooltip = report.feedback match {
        case Some(content) => laminarHelper.plaintextStringSignal(content)
        case None => laminarHelper.plaintextStringSignal(report.status match {
          case Status.NotRequested => "basic/requestScaffoldingHint"
          case Status.Pending => "basic/scaffoldingLoading"
          case Status.Error => "basic/scaffoldingError"
          case Status.Ready => "basic/scaffoldingReady"
        })
      }
      InteractionInfoPanel.infoPanelIcon(fullInfo, "scaffolding-info", icon, tooltip, Some(_ => onScaffoldingRequested())).amend(
        role := "img", tabIndex := 0, aria.label <-- tooltip,
        span(aria.hidden := true, child.text <-- laminarHelper.plaintextStringSignal("basic/scaffoldingLabel")))
    }
  }

  def deriveGradingSymbol(report: GradingSummary): Element = {
    import it.evadid.workbook.abstractions.grading.GradingStatus.*
    if !gradingReporter.isGradingSupported then div()
    else {
      val icon = report.status match {
        case NotRequested => createGradingUnrequested()
        case NotFinished => createGradingLoading()
        case TestError(_) => createGradingTestError()
        case Incorrect => createGradingNonePassed()
        case Correct => createGradingAllPassed()
        case PartiallyCorrect => createGradingSomePassed()
      }
      val (key, arguments) = report.status match {
        case NotRequested => ("basic/gradingInfoTooltipStart", Seq(report.total.toString))
        case NotFinished => ("basic/gradingInfoTooltipUnfinished", Seq(report.unfinished.toString, report.correct.toString, report.total.toString))
        case TestError(_) => ("basic/gradingInfoTooltipError", Seq(report.retries.toString, report.correct.toString, report.total.toString))
        case _ => ("basic/gradingInfoTooltipNrCorrect", Seq(report.correct.toString, report.total.toString))
      }
      val tooltip = laminarHelper.contentIdStringSignal(key, PLAINTEXT_UNDERSCORE_REPLACABLE, arguments)
      InteractionInfoPanel.infoPanelIcon(fullInfo, "grading-info", icon, tooltip, Some(_ => onGradingRequested())).amend(
        role := "img", tabIndex := 0, aria.label <-- tooltip,
        span(aria.hidden := true, child.text <-- laminarHelper.plaintextStringSignal("basic/gradingInfoLabel")))
    }
  }

  def onScaffoldingRequested(): Unit = {
    fullInfo.interactions.feedback(workbookInteractionElement).getOrCalculateScaffolding(retryErrors = true)
  }

  def onGradingRequested(): Unit = {
    fullInfo.interactions.feedback(workbookInteractionElement).getOrCalculateGrading(forceGrading = true)
  }

}
