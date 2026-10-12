package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.plot

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.editor.plot.CoordinatePlotEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.plot.CoordinatePlotInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

case object CoordinatePlotRenderer extends LineBasedRenderingFactory[CoordinatePlotInteraction] {
  override protected def createRendering(e: CoordinatePlotInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    AtomarLineRendering.basicLine(e, CoordinatePlotEditor(e, state, e.isDisabledState.toAirstreamVar).getDomElement())
  }
}
