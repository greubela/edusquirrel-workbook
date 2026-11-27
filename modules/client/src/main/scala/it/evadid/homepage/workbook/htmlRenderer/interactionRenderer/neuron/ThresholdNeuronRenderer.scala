package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.neuron

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.homepage.webElements.editor.neuron.ThresholdNeuronEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.neuron.ThresholdNeuronInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

case object ThresholdNeuronRenderer extends LineBasedRenderingFactory[ThresholdNeuronInteraction] {
  override protected def createRendering(fullInfo: FullInfo, e: ThresholdNeuronInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundVarWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation)
    val summary = p(cls := "neuron-preview", role := "status", text <-- state.signal.map(p => e.matches(p).count(identity))
      .combineWith(laminarHelper(fullInfo).plaintextStringSignal("digitalWorkbooks/neuronProgress"))
      .map((count, text) => text.replace("{count}", count.toString).replace("{total}", e.examples.size.toString)))
    AtomarLineRendering.editorCardLine(fullInfo, e, ElementCard(fullInfo, LanguageMapContentId("digitalWorkbooks/neuronTitle"), summary),
      () => ThresholdNeuronEditor(fullInfo, state, e.inputLabels, e.examples, e.initial), editorFirst = true)
  }
}
