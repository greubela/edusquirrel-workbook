package workbook.workbookHtmlElements.container

import com.raquo.laminar.api.L.Element
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class HtmlFullScreenElement() extends HtmlWorkbookElement {
  def setElementFullscreen: Unit = ???

  def clearFullscreen: Unit = ???

  def getDomElement(): Element = ???
  
}
