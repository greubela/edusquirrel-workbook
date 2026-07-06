package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement

trait AtomarLineRendering extends HtmlAppElement {

  def getDomElement(): Element = render

  lazy val render: Element
  lazy val elementsWithoutContainer: Signal[List[Element]]

  val isInteraction: Boolean

  protected lazy val lineCssStr: String = if (isInteraction) "workbook-interaction" else "workbook-element"
}