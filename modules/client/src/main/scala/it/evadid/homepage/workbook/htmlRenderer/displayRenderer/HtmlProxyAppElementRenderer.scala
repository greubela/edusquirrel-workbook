package it.evadid.homepage.workbook.htmlRenderer.displayRenderer

import it.evadid.homepage.control.model.FullInfo

import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlWorkbookElement
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.abstractions.WorkbookElement

object HtmlProxyAppElementRenderer {

  def renderWorkbookElement[T <: WorkbookElement](fullInfo: FullInfo, anyElement: T, appElement: HtmlAppElement): HtmlWorkbookElement[T, AtomarLineRendering] = {
    HtmlWorkbookElement( anyElement, AtomarLineRendering.basicLine(fullInfo, anyElement, appElement.getDomElement()))
  }


}
