package content.monks

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.webElements.HtmlAppElement

class MonksWorkbook extends HtmlAppElement{


  private val domElement: Element = div("hai :-)!")

  override def getDomElement(): L.Element = domElement
}
