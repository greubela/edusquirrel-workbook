package it.evadid.homepage.webElements

import com.raquo.laminar.api.L.Element
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper

trait HtmlAppElement {

  lazy val fullInfo: FullInfo = HtmlFullWorkbookApp.fullInfo

  def getDomElement(): Element

  protected val laminarHelper: LaminarRenderHelper = LaminarRenderHelper.singleton

}

object HtmlAppElement {

  def apply(domElement: Element): HtmlAppElement = new HtmlAppElement {
    override def getDomElement() = domElement
  }

}
