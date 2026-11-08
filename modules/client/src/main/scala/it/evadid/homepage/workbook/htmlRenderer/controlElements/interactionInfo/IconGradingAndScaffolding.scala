package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanelIcons.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay.PLAINTEXT_UNDERSCORE_REPLACABLE
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.abstractions.grading.GradedState
import it.evadid.workbook.abstractions.grading.GradingStatus.{Correct, NotFinished, NotRequested}
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.ScaffoldingRequestedButNotFinished
import it.evadid.workbook.meta.InteractionVariableGradingReporter


case class IconGradingAndScaffolding(fullInfo: FullInfo, laminarHelper: LaminarRenderHelper, workbookInteractionElement: WorkbookInteractionUntyped) {

  val gradingReporter: InteractionVariableGradingReporter[?, ?, ?] = InteractionVariableGradingReporter(workbookInteractionElement.interactionVariable)

  val domElement: Element = div(cls := "interaction-grading",
    button(typ := "button", cls := "interaction-grading__request",
      onClick --> (_ => workbookInteractionElement.interactionVariable.control.getOrCalculateGrading(fullInfo.executionContextInformation, true)))
  )


  def deriveScaffoldingSymbol(curScaffolding: Option[ScaffoldingResult[?, ?]]): Element =
    if (workbookInteractionElement.scaffoldingElement.isEmpty) div()
    else if (curScaffolding.isEmpty) {
      val curScaffLabel =laminarHelper.plaintextStringSignal("basic/requestScaffoldingHint")
      InteractionInfoPanel.infoPanelIcon(
        "scaffolding-info",
        createScaffoldingNothing(),
        curScaffLabel,
        Some(_ => onScaffoldingRequested())
      ).amend(
        role := "img", tabIndex := 0, aria.label <-- curScaffLabel,
        span(aria.hidden := true, child.text <-- laminarHelper.plaintextStringSignal("basic/scaffoldingLabel"))
      )



    } else {
      val status = curScaffolding.get.scaffoldingStatus
      val svgScaffoldingIcon = if (status == ScaffoldingRequestedButNotFinished()) {
        createScaffoldingLoading()
      } else if (status.isError) {
        createScaffoldingError()
      } else {
        createScaffoldingFinished()
      }

      val scaffoldingTooltip = status.getInfoString.match {
        case Left(langMap) => fullInfo.signals.currentLanguage.map(language =>
          if langMap.availableLanguages.nonEmpty then langMap.getWithLanguagePreference(List(language, English)) else "")
        case Right(langMapId) => laminarHelper.plaintextStringSignal(langMapId)
      }

      InteractionInfoPanel.infoPanelIcon(
        "scaffolding-info",
        svgScaffoldingIcon,
        scaffoldingTooltip,
        Some(_ => onScaffoldingRequested())
      ).amend(
        role := "img", tabIndex := 0, aria.label <-- scaffoldingTooltip,
        span(aria.hidden := true, child.text <-- laminarHelper.plaintextStringSignal("basic/scaffoldingLabel"))
      )
    }

  def deriveGradingSymbol(gradedState: GradedState[?, ?]): Element = if (workbookInteractionElement.gradingElements.isEmpty) div() else {
    val gradingResults = gradedState.graderResultMap.values

    val availableGraders = workbookInteractionElement.gradingElements.size
    val notRequested = gradingResults.count(_.gradingStatus == NotRequested())
    val notFinished = gradingResults.map(_.gradingStatus).count(_ == NotFinished())
    val testError = gradingResults.map(_.gradingStatus).count(_.isError)
    val shouldRetry = gradingResults.map(_.gradingStatus).count(_.shouldRetryExecution)
    val correct = gradingResults.count(_.gradingStatus.isCorrect)
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


    InteractionInfoPanel.infoPanelIcon(
      "grading-info",
      svgGradingIcon,
      svgGradingTooltip,
      Some(_ => onGradingRequested())
    ).amend(
      role := "img", tabIndex := 0, aria.label <-- svgGradingTooltip,
      span(aria.hidden := true, child.text <-- laminarHelper.plaintextStringSignal("basic/gradingInfoLabel"))
    )
  }

  def onScaffoldingRequested(): Unit = {
    workbookInteractionElement.interactionVariable.control.getOrCalculateScaffolding(fullInfo.executionContextInformation)
  }

  def onGradingRequested(): Unit = {
    workbookInteractionElement.interactionVariable.control.getOrCalculateGrading(fullInfo.executionContextInformation, forceGrading = true)
  }

}
