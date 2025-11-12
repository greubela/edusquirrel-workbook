package contentmanagement.webElements.genericHtmlElements.other

import com.raquo.laminar.api.L
import contentmanagement.webElements.HtmlAppElement

case class HtmlTab(tabNr: Int, tabDiv: L.HtmlElement, tabLabel: String)

case class HtmlTabElement(tabs: List[HtmlTab], onTabSwitched: (HtmlTab, HtmlTab) => Any) extends HtmlAppElement {


  override def getDomElement(): L.Element = ???

  
}
