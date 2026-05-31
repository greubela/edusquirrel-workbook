package it.evadid.homepage.workbook.content

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import it.evadid.homepage.webElements.HtmlAppElement

class MonksWorkbook extends HtmlAppElement{

  private val domElement: Element = div("hai :-)!")

  override def getDomElement(): L.Element = domElement
}
  