package it.evadid.homepage.workbook.htmlRenderer.displayRenderer

import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlWorkbookElement
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.abstractions.WorkbookElement

object HtmlProxyAppElementRenderer {

  def renderWorkbookElement[T <: WorkbookElement](anyElement: T, appElement: HtmlAppElement): HtmlWorkbookElement[T, AtomarLineRendering] = {
    HtmlWorkbookElement(HtmlFullWorkbookApp.fullInfo, anyElement, AtomarLineRendering.basicLine(anyElement, appElement.getDomElement()))
  }


}
