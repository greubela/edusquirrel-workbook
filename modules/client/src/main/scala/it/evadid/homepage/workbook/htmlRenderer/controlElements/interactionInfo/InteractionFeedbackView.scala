package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingTestGrader
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingUnitTestGrader
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{CommandUsageMeasure, RecreateShapeGrader, TurtleCommandUsageGrader}
import it.evadid.workbook.meta.InteractionVariableGradingReporter
import scala.concurrent.{ExecutionContext, Future}

/** Presentation shared by the sidebar and compact line; requests still belong to the existing control. */
private[interactionInfo] final class InteractionFeedbackView(
    val interaction: WorkbookInteractionUntyped,
    reporter: InteractionVariableGradingReporter[?, ?, ?],
    fullInfo: FullInfo,
    helper: LaminarRenderHelper
) {
  private given ExecutionContext = ExecutionContext.global
  private val variable = interaction.interactionVariable
  private val requested = new EventBus[Unit]
  private val changes = EventStream.merge(reporter.currentHistorySignal.toEventStream().mapTo(()), requested.events)

  private def summaryNow: GradingSummary =
    GradingSummary(interaction.gradingElements, variable.control.getCurrentStateOfGrading(variable.currentValue))
  private def scaffoldingNow: Option[ScaffoldingResult[?, ?]] =
    variable.control.getCurrentStateOfScaffolding(variable.currentValue)

  val disabled: Signal[Boolean] = interaction.isDisabledState.observable.toEventStream().toSignal(interaction.isDisabledState.now())
  val grading: Signal[GradingSummary] = changes.map(_ => summaryNow).toSignal(summaryNow)
  val scaffolding: Signal[Option[ScaffoldingResult[?, ?]]] = changes.map(_ => scaffoldingNow).toSignal(scaffoldingNow)

  def label(key: String): Signal[String] = helper.plaintextStringSignal(LanguageMapContentId(s"basic/$key"))
  def localized(content: LanguageMap[HumanLanguage]): Signal[String] = fullInfo.signals.currentLanguage.map { language =>
    if content.availableLanguages.isEmpty then "" else content.getWithLanguagePreference(List(language, English))
  }

  def description(entry: GradingSummary.Entry): Signal[String] = entry.grader match {
    case grader: ProgrammingUnitTestGrader => grader.publicDescription
      .map(helper.plaintextStringSignal).getOrElse(Val(grader.testId))
    case _: RecreateShapeGrader => label("gradingShapeDescription")
    case grader: TurtleCommandUsageGrader =>
      val measure = if grader.measure == CommandUsageMeasure.OccurrencesInCode then "gradingUsageCode" else "gradingUsageExecution"
      label(measure).map(template => template.replace("{command}", grader.command.universalInterpretation())
        .replace("{maximum}", grader.maximum.toString))
    case grader: ProgrammingTestGrader => Val(grader.testId)
    // Internal LLM instructions and serialized graders are not public descriptions.
    case _ => label("gradingTestNumber").map(_.replace("{number}", entry.index.toString))
  }

  def feedback(entry: GradingSummary.Entry): Signal[String] = entry.result.flatMap(_.feedbackInformation)
    .filter(_.availableLanguages.nonEmpty).map(localized).getOrElse(label("gradingNoFeedback"))

  def scaffoldingLabel(result: Option[ScaffoldingResult[?, ?]]): String = result.map(_.scaffoldingStatus) match {
    case None => "gradingGroupNotRequested"
    case Some(_: ScaffoldingStatus.ScaffoldingRequestedButNotFinished) => "gradingGroupPending"
    case Some(status) if status.isError => "gradingGroupError"
    case _ => "scaffoldingReady"
  }

  def scaffoldingText(result: Option[ScaffoldingResult[?, ?]]): Signal[String] = result.map(_.scaffoldingStatus) match {
    case Some(ScaffoldingStatus.ScaffoldingFeedback(content)) => localized(content)
    case _ => label(scaffoldingLabel(result))
  }

  private def request(operation: => Future[?]): Unit = {
    val result = scala.util.Try(operation).fold(Future.failed, identity)
    requested.emit(())
    result.onComplete { outcome =>
      outcome.failed.foreach(error => fullInfo.loggerSystemInfo.uiAndDomLogger.logExceptionWarn("Could not request interaction feedback", error))
      requested.emit(())
    }
  }
  def requestGrading(): Unit = request(variable.control.getOrCalculateGrading(fullInfo.workbookContextInformation, forceGrading = true))
  def requestScaffolding(): Unit = request(variable.control.getOrCalculateScaffolding(fullInfo.workbookContextInformation))
}
