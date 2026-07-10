package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{children, *}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.homepage.workbook.htmlRenderer.displayRenderer.HtmlDisplayLangMapContentRenderer.contentIdStringSignal

case class ElementCard(headline: LanguageMapContentId, content: DomElementCollection) extends HtmlAppElement {

  lazy val labelElement: Element = h3(
    cls := "element-card-label",
    text <-- contentIdStringSignal(headline)
  )

  lazy val contentElement: Element = div(
    cls := "element-card-content",
    children <-- content.allElementsSignal
  )

  lazy val cardElement: Element = div(
    cls := "element-card",
    labelElement,
    contentElement
  )

  lazy val domElement: Element = cardElement


  override def getDomElement(): L.Element = domElement
}

object ElementCard {





}