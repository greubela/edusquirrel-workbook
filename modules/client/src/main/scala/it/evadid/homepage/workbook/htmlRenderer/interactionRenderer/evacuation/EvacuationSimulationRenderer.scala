package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.evacuation

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.evacuation.EvacuationSimulationEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.evacuation.EvacuationSimulationInteraction

case object EvacuationSimulationRenderer extends LineBasedRenderingFactory[EvacuationSimulationInteraction] {
  override protected def createRendering(e: EvacuationSimulationInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl,
      it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR).toAirstreamVar
    val open = HtmlButtonElement.withTextLabel("basic/OpenEditor", _ =>
      fullInfo.displayControl.setFullscreen(EvacuationSimulationEditor(state, e.initial, e.isDisabledState.toAirstreamVar.signal)))
    val preview = p(cls := "evacuation-simulation-preview", role := "status",
      text <-- state.signal.combineWith(laminarHelper.plaintextStringSignal("digitalWorkbooks/evacuationSimulationPreview"))
        .map((value, template) => template.replace("{runs}", value.measurements.size.toString)
          .replace("{people}", value.floor.people.size.toString).replace("{exits}", value.floor.exitCount.toString)))
    AtomarLineRendering.cardLine(e, List(ElementCard(LanguageMapContentId("digitalWorkbooks/evacuationSimulationTitle"), preview),
      ElementCard(LanguageMapContentId("basic/openEditor"), open.getDomElement())))
  }
}
