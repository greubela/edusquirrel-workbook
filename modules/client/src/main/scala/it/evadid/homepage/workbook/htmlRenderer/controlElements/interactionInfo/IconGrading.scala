package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay.PLAINTEXT_UNDERSCORE_REPLACABLE
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.abstractions.grading.GradedState
import it.evadid.workbook.abstractions.grading.GradingStatus.{Correct, NotFinished, NotRequested}
import it.evadid.workbook.meta.InteractionVariableGradingReporter

object IconGrading {

  private case class GradingView(mount: Option[AnyRef] = None, revision: Long = 0,
                                 pending: Option[AnyRef] = None, failed: Boolean = false)


}


case class IconGrading(fullInfo: FullInfo, laminarHelper: LaminarRenderHelper, workbookInteractionElement: WorkbookInteractionUntyped) {

  val gradingReporter: InteractionVariableGradingReporter[?, ?, ?] = InteractionVariableGradingReporter(workbookInteractionElement.interactionVariable)

  val domElement: Element = div(cls := "interaction-grading",
    button(typ := "button", cls := "interaction-grading__request",
      onClick --> (_ => workbookInteractionElement.interactionVariable.control.getOrCalculateGrading(fullInfo.executionContextInformation, true)))
  )


  def deriveGradingSymbol(gradedState: GradedState[?, ?]): Element = if (workbookInteractionElement.gradingElements.isEmpty) div() else {

    val gradingResults = gradedState.graderResultMap.values

    val availableGraders = workbookInteractionElement.gradingElements.size
    val notRequested = gradingResults.map(_.gradingStatus).count(_ == NotRequested())
    val notFinished = gradingResults.map(_.gradingStatus).count(_ == NotFinished())
    val testError = gradingResults.map(_.gradingStatus).count(_.isError)
    val shouldRetry = gradingResults.map(_.gradingStatus).count(_.shouldRetryExecution)
    val correct = gradingResults.count(_ == Correct())
    val incorrect = availableGraders - correct

    val svgGradingIcon =
      if (notRequested > 0) {
        createGradingUnrequested()
      } else if (notFinished > 0) {
        createGradingLoading()
      } else if (testError > 0 || shouldRetry > 0) {
        createGradingTestError()
      } else if (correct == 0 && incorrect > 0) {
        createGradingNonePassed()
      } else if (correct > 0 && incorrect == 0) {
        createGradingAllPassed()
      } else {
        createGradingSomePassed()
      }

    val svgGradingTooltip: Signal[String] =
      if (notRequested > 0) {
        laminarHelper.contentIdStringSignal("basic/gradingInfoTooltipStart", PLAINTEXT_UNDERSCORE_REPLACABLE, List("" + availableGraders))
      } else if (notFinished > 0) {
        laminarHelper.contentIdStringSignal("basic/gradingInfoTooltipUnfinished", PLAINTEXT_UNDERSCORE_REPLACABLE, List("" + notFinished, "" + correct, "" + availableGraders))
      } else if (testError > 0 || shouldRetry > 0) {
        laminarHelper.contentIdStringSignal("basic/gradingInfoTooltipError", PLAINTEXT_UNDERSCORE_REPLACABLE, List("" + shouldRetry, "" + correct, "" + availableGraders))
      } else {
        laminarHelper.contentIdStringSignal("basic/gradingInfoTooltipNrCorrect", PLAINTEXT_UNDERSCORE_REPLACABLE, List("" + correct, "" + availableGraders))
      }

    InteractionInfoPanel.infoPanelIcon("grading-info", svgGradingIcon, svgGradingTooltip).amend(
      role := "img", tabIndex := 0, aria.label <-- svgGradingTooltip,
      span(aria.hidden := true, child.text <-- laminarHelper.plaintextStringSignal("basic/gradingInfoLabel")),
      onClick --> (_ => workbookInteractionElement.interactionVariable.control.getOrCalculateGrading(fullInfo.executionContextInformation, forceGrading = true))
    )
  }

}
