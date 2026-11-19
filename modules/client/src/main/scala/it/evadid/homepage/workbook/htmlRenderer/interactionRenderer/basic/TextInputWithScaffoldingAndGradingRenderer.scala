package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.editor.SimpleTextEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionHintPanel
import it.evadid.workbook.abstractions.grading.GradingStatus
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading


object TextInputWithScaffoldingAndGradingRenderer extends LineBasedRenderingFactory[TextInputWithScaffoldingAndGrading] {
  override protected def createRendering(element: TextInputWithScaffoldingAndGrading): AtomarLineRendering = {
    val variable = element.interactionVariable
    val answer = variable.createBoundStateWithUpdateLogic( TextInteraction.decideTextareaUpdateImportance, fullInfo.workbookContextInformation).toAirstreamVar
    def label(key: String) = laminarHelper.plaintextStringSignal(LanguageMapContentId(s"basic/$key"))
    val reporter = fullInfo.signals.reportersFor(element).grading
    val feedback: Signal[Seq[Element]] = reporter.summary.map { grading =>
      grading.entries.flatMap(_.result).map { result =>
        val key = result.gradingStatus match {
          case GradingStatus.Correct() => "textGradingCorrect"
          case GradingStatus.Incorrect() => "textGradingIncorrect"
          case GradingStatus.PartiallyCorrect() => "textGradingPartiallyCorrect"
          case GradingStatus.TestError(_) => "gradingError"
          case GradingStatus.NotFinished() => "gradingLoading"
          case GradingStatus.NotRequested() => "gradingNotRun"
        }
        div(cls := "text-input-feedback__result",
          strong(child.text <-- label(key)),
          result.feedbackInformation.filter(_.availableLanguages.nonEmpty).map(content =>
            p(cls := "interaction-scaffolding__message", child.text <-- laminarHelper.plaintextStringSignal(content))))
      }
    }
    val content = div(
      cls := "text-input-feedback",
      p(cls := "text-input-feedback__task", child.text <-- laminarHelper.plaintextStringSignal(element.instructionText)),
      SimpleTextEditor(answer).getDomElement().amend(aria.label <-- laminarHelper.plaintextStringSignal(element.instructionText)),
      Option.when(element.gradingElements.nonEmpty || element.scaffoldingElement.nonEmpty)(
        div(cls := "interaction-scaffolding",
          p(cls := "interaction-scaffolding__notice", child.text <-- label("gradingNotice")),
          div(role := "status", aria.live := "polite",
            children <-- feedback),
          InteractionHintPanel(reporter).getDomElement()))
    )
    // basicLine supplies the shared grading, synchronization and hint controls.
    AtomarLineRendering.basicLine(element, content)
  }
}
