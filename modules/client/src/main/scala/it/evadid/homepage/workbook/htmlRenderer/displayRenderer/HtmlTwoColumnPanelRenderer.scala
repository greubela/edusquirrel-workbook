package it.evadid.homepage.workbook.htmlRenderer.displayRenderer

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.displayElements.TwoColumnPanel

object HtmlTwoColumnPanelRenderer extends LineBasedRenderingFactory[TwoColumnPanel] {
  override protected def createRendering(fullInfo: FullInfo, workbookElement: TwoColumnPanel): AtomarLineRendering =
    AtomarLineRendering.basicLine(fullInfo, workbookElement, div(
      cls := "workbook-two-column-panel",
      List(workbookElement.left, workbookElement.right).map(element => div(HtmlRenderFactory.render(fullInfo, element).rendering.getDomElement()))
    ))
}
