package it.evadid.homepage.webElements

import com.raquo.laminar.api.L.Element
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper

trait HtmlAppElement {

  def dismissOnOutsideClick: Boolean = true

  def getDomElement(): Element

  /** Rendering helpers for the page state the element was created for. */
  protected def laminarHelper(fullInfo: FullInfo): LaminarRenderHelper = LaminarRenderHelper(fullInfo)

}

object HtmlAppElement {

  def apply(domElement: Element): HtmlAppElement = new HtmlAppElement {
    override def getDomElement() = domElement
  }

}
