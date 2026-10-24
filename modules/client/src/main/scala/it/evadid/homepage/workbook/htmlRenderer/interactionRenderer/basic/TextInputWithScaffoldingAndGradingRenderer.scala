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

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object TextInputWithScaffoldingAndGradingRenderer extends LineBasedRenderingFactory[TextInputWithScaffoldingAndGrading] {
  override protected def createRendering(element: TextInputWithScaffoldingAndGrading): AtomarLineRendering = {
    val variable = element.interactionVariable
    val answer = variable.createBoundStateWithUpdateLogic(fullInfo.syncControl, TextInteraction.decideTextareaUpdateImportance).toAirstreamVar
    val request = Var(Option.empty[AnyRef])
    def label(key: String) = laminarHelper.plaintextStringSignal(LanguageMapContentId(s"basic/$key"))
    def grade(): Unit = if request.now().isEmpty then {
      val token = new Object
      request.set(Some(token))
      variable.startGrading(fullInfo.syncControl, List(fullInfo.defaults.backendExecutor), forceGrading = true,
        resolver = Some(fullInfo.signals.idResolver)).flatMap { _ =>
        fullInfo.syncControl.requestStore(variable)
      }.onComplete { _ =>
        if request.now().contains(token) then request.set(None)
      }
    }
    val feedback: Signal[List[Element]] = {
      val history = variable.observableState.toEventStream().toSignal(variable.history)
      history.combineWith(answer.signal, fullInfo.signals.currentLanguage).map { (saved, source, language) =>
        element.gradingElements.flatMap { grader =>
          saved.gradings.get(source).toList.flatMap(_.testsWithResults).filter(_.grader == grader)
            .maxByOption(_.gradingTime).toList.filter(result => result.feedbackInformation.isEmpty ||
              result.feedbackInformation.flatMap(_.tryGetInLanguage(language)).nonEmpty).map { result =>
              val key = result.gradingStatus match {
                case GradingStatus.Correct() => "gradingCorrect"
                case GradingStatus.PartiallyCorrect() => "gradingPartiallyCorrect"
                case GradingStatus.Incorrect() => "gradingIncorrect"
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
      onUnmountCallback(_ => request.set(None)),
      p(cls := "text-input-feedback__task", child.text <-- laminarHelper.plaintextStringSignal(element.exerciseText)),
      SimpleTextEditor(answer).getDomElement().amend(aria.label <-- laminarHelper.plaintextStringSignal(element.exerciseText)),
      Option.when(element.gradingElements.nonEmpty)(
        div(cls := "interaction-scaffolding",
          div(cls := "interaction-scaffolding__actions",
            button(typ := "button", cls := "text-input-feedback__grade interaction-scaffolding__request",
              disabled <-- request.signal.map(_.nonEmpty),
              child.text <-- request.signal.flatMapSwitch(pending => label(if pending.nonEmpty then "gradingLoading" else "gradingRequest")),
              onClick --> (_ => grade()))),
          p(cls := "interaction-scaffolding__notice", child.text <-- label("gradingNotice")),
          div(role := "status", aria.live := "polite", aria.busy <-- request.signal.map(_.nonEmpty),
            children <-- feedback)))
    )
    // basicLine supplies the standard sync/hint controls for this same interaction.
    AtomarLineRendering.basicLine(element, content)
  }
}
