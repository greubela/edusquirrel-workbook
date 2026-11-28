package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.evacuation

import it.evadid.workbook.interaction.state.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.basic.LabelSelection
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.editor.evacuation.ScenarioEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.evacuation.EvacuationConstructFloorInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR

case object EvacuationConstructFloorRenderer extends LineBasedRenderingFactory[EvacuationConstructFloorInteraction] {
  override protected def createRendering(fullInfo: FullInfo, e: EvacuationConstructFloorInteraction): AtomarLineRendering = {
    val state = fullInfo.interactions.boundVar(e, MAJOR)
    val preview = p(cls := "evacuation-preview", role := "status",
      text <-- state.signal.combineWith(laminarHelper(fullInfo).plaintextStringSignal("digitalWorkbooks/evacuationPreview"))
        .map((plan, template) => template.replace("{cols}", plan.cols.toString).replace("{rows}", plan.rows.toString)
          .replace("{people}", plan.people.size.toString).replace("{exits}", plan.exitCount.toString)),
      span(text <-- LabelSelection(state.signal.map(e.requirements.isSatisfiedBy),
        Seq("digitalWorkbooks/evacuationComplete", "digitalWorkbooks/evacuationIncomplete"), laminarHelper(fullInfo).plaintextStringSignal)(passed =>
          Some(if (passed) "digitalWorkbooks/evacuationComplete" else "digitalWorkbooks/evacuationIncomplete"))))
    AtomarLineRendering.editorCardLine(fullInfo, e, ElementCard(fullInfo, LanguageMapContentId("digitalWorkbooks/evacuationTitle"), preview),
      () => ScenarioEditor(fullInfo, state, e.initial, e.isDisabledState.toAirstreamVar.signal, e.requirements))
  }
}
