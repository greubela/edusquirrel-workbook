package it.evadid.homepage.webElements

import com.raquo.laminar.api.L.Element
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper

trait HtmlAppElement {
  def getDomElement(): Element

  protected val laminarHelper: LaminarRenderHelper = LaminarRenderHelper.singleton

}

object HtmlAppElement {

  def apply(domElement: Element): HtmlAppElement = new HtmlAppElement {
    override def getDomElement() = domElement
  }

}
