package it.evadid.homepage.workbook.htmlRenderer

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.model.abstractions.WorkbookElement

case class HtmlWorkbookElement[T <: WorkbookElement](fullInfo: FullInfo, workbookElement: T, rendering: Element) extends HtmlAppElement {

  override def getDomElement(): Element = rendering

}
