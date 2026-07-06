package it.evadid.homepage.webElements

import com.raquo.laminar.api.L.Element

trait HtmlAppElement {
  def getDomElement(): Element
}

object HtmlAppElement {

  def apply(domElement: Element): HtmlAppElement = new HtmlAppElement {
    override def getDomElement() = domElement
  }

}
