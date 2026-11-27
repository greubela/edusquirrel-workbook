package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.compression

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.compression.{CompressionEditor, CompressionActivitiesEditor, CompressionExperimentView}
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.compression.CompressionExperimentInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

case object CompressionExperimentRenderer extends LineBasedRenderingFactory[CompressionExperimentInteraction] {
  override protected def createRendering(fullInfo: FullInfo, e: CompressionExperimentInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar
    e.initial match {
      case _: it.evadid.workbook.model.compression.WrittenAnswer | _: it.evadid.workbook.model.compression.EthicalReflection | _: it.evadid.workbook.model.compression.PreviousAnswer =>
        return AtomarLineRendering.basicLine(fullInfo, e, CompressionActivitiesEditor(fullInfo, state,e.initial,e.isDisabledState.toAirstreamVar.signal).getDomElement(), "compression-inline")
      case _ => ()
    }
    val open = HtmlButtonElement.withTextLabel(fullInfo, "CompressionWorkbook/openExperiment", _ =>
      fullInfo.displayControl.setFullscreen(CompressionEditor(fullInfo, state, e.initial, e.isDisabledState.toAirstreamVar.signal)))
    val preview = div(cls := "compression-preview", p(role := "status", text <-- CompressionExperimentView.summary(fullInfo, state.signal)), open.getDomElement())
    AtomarLineRendering.cardLine(fullInfo, e, List(ElementCard(fullInfo, e.title, preview)))
  }
}
