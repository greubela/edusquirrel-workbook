package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.plot

import it.evadid.workbook.control.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.editor.plot.CoordinatePlotEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.interactions.plot.CoordinatePlotInteraction
import it.evadid.workbook.sync.UpdateImportance

case object CoordinatePlotRenderer extends LineBasedRenderingFactory[CoordinatePlotInteraction] {
  override protected def createRendering(fullInfo: FullInfo, e: CoordinatePlotInteraction): AtomarLineRendering = {
    val state = fullInfo.interactions.boundVar(e, UpdateImportance.MAJOR)
    AtomarLineRendering.basicLine(fullInfo, e, CoordinatePlotEditor(fullInfo, e, state, e.isDisabledState.toAirstreamVar).getDomElement())
  }
}
