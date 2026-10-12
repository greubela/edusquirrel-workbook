package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.evacuation

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.evacuation.ScenarioEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.evacuation.EvacuationConstructFloorInteraction

case object EvacuationConstructFloorRenderer extends LineBasedRenderingFactory[EvacuationConstructFloorInteraction] {
  override protected def createRendering(e: EvacuationConstructFloorInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl,
      it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR).toAirstreamVar
    val open = HtmlButtonElement.withTextLabel("basic/OpenEditor", _ =>
      fullInfo.displayControl.setFullscreen(ScenarioEditor(state, e.initial, e.isDisabledState.toAirstreamVar.signal, e.requirements)))
    val preview = p(cls := "evacuation-preview", role := "status",
      text <-- state.signal.combineWith(laminarHelper.plaintextStringSignal("digitalWorkbooks/evacuationPreview"))
        .map((plan, template) => template.replace("{cols}", plan.cols.toString).replace("{rows}", plan.rows.toString)
          .replace("{people}", plan.people.size.toString).replace("{exits}", plan.exitCount.toString)),
      span(text <-- state.signal.map(e.requirements.isSatisfiedBy).flatMapSwitch(passed =>
        laminarHelper.plaintextStringSignal(if (passed) "digitalWorkbooks/evacuationComplete" else "digitalWorkbooks/evacuationIncomplete"))))
    AtomarLineRendering.cardLine(e, List(ElementCard(LanguageMapContentId("digitalWorkbooks/evacuationTitle"), preview),
      ElementCard(LanguageMapContentId("basic/openEditor"), open.getDomElement())))
  }
}
