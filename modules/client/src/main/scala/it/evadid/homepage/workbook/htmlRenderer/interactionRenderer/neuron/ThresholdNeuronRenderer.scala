package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.neuron

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.neuron.ThresholdNeuronEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.neuron.ThresholdNeuronInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

case object ThresholdNeuronRenderer extends LineBasedRenderingFactory[ThresholdNeuronInteraction] {
  override protected def createRendering(e: ThresholdNeuronInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    val open = HtmlButtonElement.withTextLabel("basic/OpenEditor", _ =>
      fullInfo.displayControl.setFullscreen(ThresholdNeuronEditor(state, e.inputLabels, e.examples, e.initial)))
    val summary = p(cls := "neuron-preview", role := "status", text <-- state.signal.map(p => e.matches(p).count(identity))
      .combineWith(laminarHelper.plaintextStringSignal("digitalWorkbooks/neuronProgress"))
      .map((count, text) => text.replace("{count}", count.toString).replace("{total}", e.examples.size.toString)))
    AtomarLineRendering.cardLine(e, List(ElementCard(LanguageMapContentId("basic/openEditor"), open.getDomElement()),
      ElementCard(LanguageMapContentId("digitalWorkbooks/neuronTitle"), summary)))
  }
}
