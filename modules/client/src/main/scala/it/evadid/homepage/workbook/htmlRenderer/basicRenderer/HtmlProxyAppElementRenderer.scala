package it.evadid.homepage.workbook.htmlRenderer.basicRenderer

import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlWorkbookElement
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, RenderingLine}
import it.evadid.workbook.model.abstractions.WorkbookElement

object HtmlProxyAppElementRenderer {

  def renderWorkbookElement[T <: WorkbookElement](anyElement: T, appElement: HtmlAppElement): HtmlWorkbookElement[T, AtomarLineRendering] = {
    HtmlWorkbookElement(HtmlFullWorkbookApp.fullInfo, anyElement, RenderingLine(false, appElement.getDomElement()))
  }


}
