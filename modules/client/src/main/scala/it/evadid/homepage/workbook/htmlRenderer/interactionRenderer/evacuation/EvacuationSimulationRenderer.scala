package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.evacuation

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.{InteractionVariableOnJS, StateBasedVar}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.editor.evacuation.EvacuationSimulationEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.evacuation.EvacuationSimulationInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR

case object EvacuationSimulationRenderer extends LineBasedRenderingFactory[EvacuationSimulationInteraction] {
  override protected def createRendering(fullInfo: FullInfo, e: EvacuationSimulationInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundVarWithUpdateImportance(MAJOR, fullInfo.workbookContextInformation)
    val preview = p(cls := "evacuation-simulation-preview", role := "status",
      text <-- state.signal.combineWith(laminarHelper(fullInfo).plaintextStringSignal("digitalWorkbooks/evacuationSimulationPreview"))
        .map((value, template) => template.replace("{runs}", value.measurements.size.toString)
          .replace("{people}", value.floor.people.size.toString).replace("{exits}", value.floor.exitCount.toString)))
    AtomarLineRendering.editorCardLine(fullInfo, e, ElementCard(fullInfo, LanguageMapContentId("digitalWorkbooks/evacuationSimulationTitle"), preview),
      () => EvacuationSimulationEditor(fullInfo, state, e.initial, e.isDisabledState.toAirstreamVar.signal))
  }
}
