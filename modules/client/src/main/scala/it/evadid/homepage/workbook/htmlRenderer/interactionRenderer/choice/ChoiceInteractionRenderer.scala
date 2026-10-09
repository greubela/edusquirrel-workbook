package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.choice

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

case object ChoiceInteractionRenderer extends LineBasedRenderingFactory[ChoiceInteraction] {
  override protected def createRendering(e: ChoiceInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    val controls = fieldSet(cls := "choice-interaction",
      legend(text <-- laminarHelper.plaintextStringSignal(e.prompt)),
      e.options.zipWithIndex.map { (optionId, i) => label(
        input(typ := (if e.allowMultiple then "checkbox" else "radio"), nameAttr := e.elementId,
          checked <-- state.signal.map(_.selected.contains(i)),
          disabled <-- e.isDisabledState.toAirstreamVar.signal,
          onChange.mapToChecked --> (checked => state.update(answer => e.select(answer, i, checked)))),
        span(text <-- laminarHelper.plaintextStringSignal(optionId))) },
      p(cls := "choice-feedback", role := "status", aria.live := "polite",
        text <-- state.signal.map(answer => if !e.isAnswered(answer) then "unanswered" else e.isCorrect(answer) match {
          case None => "answerSaved"
          case Some(true) => "correct"
          case Some(false) => "tryAgain"
        }).flatMapSwitch(key => laminarHelper.plaintextStringSignal(s"digitalWorkbooks/$key")))
    )
    AtomarLineRendering.basicLine(e, controls)
  }
}
