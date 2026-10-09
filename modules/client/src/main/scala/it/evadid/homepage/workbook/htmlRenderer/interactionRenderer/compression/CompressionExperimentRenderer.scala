package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.compression

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.compression.{CompressionEditor, CompressionExperimentView}
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.compression.CompressionExperimentInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

case object CompressionExperimentRenderer extends LineBasedRenderingFactory[CompressionExperimentInteraction] {
  override protected def createRendering(e: CompressionExperimentInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    val open = HtmlButtonElement.withTextLabel("CompressionWorkbook/openExperiment", _ =>
      fullInfo.displayControl.setFullscreen(CompressionEditor(state, e.initial, e.isDisabledState.toAirstreamVar.signal)))
    val preview = div(cls := "compression-preview", p(role := "status", text <-- state.signal.flatMapSwitch(CompressionExperimentView.summary)), open.getDomElement())
    AtomarLineRendering.cardLine(e, List(ElementCard(e.title, preview)))
  }
}
