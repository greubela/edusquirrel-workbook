package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{children, *}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection

/** Titled card. A `collapsible` card starts expanded; its headline toggles the content. */
case class ElementCard(fullInfo: FullInfo, headline: LanguageMapContentId, content: DomElementCollection, collapsible: Boolean = false) extends HtmlAppElement {

  private lazy val collapsed: Var[Boolean] = Var(false)

  private def headlineText: Modifier[HtmlElement] = text <-- laminarHelper(fullInfo).plaintextStringSignal(headline)

  lazy val labelElement: Element =
    if collapsible then h3(
      cls := "element-card-label",
      button(
        typ := "button",
        cls := "element-card-toggle",
        aria.expanded <-- collapsed.signal.map(!_),
        onClick --> { _ => collapsed.update(!_) },
        span(cls := "element-card-toggle-icon", aria.hidden := true, child.text <-- collapsed.signal.map(if _ then "+" else "−")),
        span(headlineText)
      )
    )
    else h3(cls := "element-card-label", headlineText)

  lazy val contentElement: Element = div(
    cls := "element-card-content",
    Option.when(collapsible)(hidden <-- collapsed.signal),
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
