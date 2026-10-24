package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.{StateBasedVar, RichObservableValue}
import it.evadid.homepage.webElements.editor.SimpleTextEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.abstractions.grading.GradingStatus
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading


object TextInputWithScaffoldingAndGradingRenderer extends LineBasedRenderingFactory[TextInputWithScaffoldingAndGrading] {
  override protected def createRendering(element: TextInputWithScaffoldingAndGrading): AtomarLineRendering = {
    val variable = element.interactionVariable
    val answer = variable.createBoundStateWithUpdateLogic(fullInfo.syncControl, TextInteraction.decideTextareaUpdateImportance).toAirstreamVar
    def label(key: String) = laminarHelper.plaintextStringSignal(LanguageMapContentId(s"basic/$key"))
    val feedback: Signal[List[Element]] = {
      val history = variable.observableState.toEventStream().toSignal(variable.history)
      history.combineWith(answer.signal, fullInfo.signals.currentLanguage).map { (saved, source, language) =>
        element.gradingElements.flatMap { grader =>
          saved.gradings.get(source).toList.flatMap(_.testsWithResults).filter(_.grader == grader)
            .maxByOption(_.gradingTime).toList.filter(result => result.feedbackInformation.isEmpty ||
              result.feedbackInformation.flatMap(_.tryGetInLanguage(language)).nonEmpty).map { result =>
              val key = result.gradingStatus match {
                case GradingStatus.Correct() => "textGradingCorrect"
                case GradingStatus.PartiallyCorrect() => "textGradingPartiallyCorrect"
                case GradingStatus.Incorrect() => "textGradingIncorrect"
                case _: GradingStatus.TestError => "gradingError"
                case GradingStatus.NotExecuted() => "gradingNotExecuted"
              }
              div(cls := "text-input-feedback__result",
                strong(child.text <-- label(key)),
                result.feedbackInformation.flatMap(_.tryGetInLanguage(language)).map(text =>
                  p(cls := "interaction-scaffolding__message", text)))
            }
        }
      }
    }
    val content = div(
      cls := "text-input-feedback",
      p(cls := "text-input-feedback__task", child.text <-- laminarHelper.plaintextStringSignal(element.exerciseText)),
      SimpleTextEditor(answer).getDomElement().amend(aria.label <-- laminarHelper.plaintextStringSignal(element.exerciseText)),
      Option.when(element.gradingElements.nonEmpty)(
        div(cls := "interaction-scaffolding",
          p(cls := "interaction-scaffolding__notice", child.text <-- label("gradingNotice")),
          div(role := "status", aria.live := "polite",
            children <-- feedback)))
    )
    // basicLine supplies the shared grading, synchronization and hint controls.
    AtomarLineRendering.basicLine(element, content)
  }
}
