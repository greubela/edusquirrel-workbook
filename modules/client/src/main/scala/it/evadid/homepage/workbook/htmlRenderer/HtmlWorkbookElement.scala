package it.evadid.homepage.workbook.htmlRenderer

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.abstractions.WorkbookElement

case class HtmlWorkbookElement[W <: WorkbookElement, A <: HtmlAppElement](fullInfo: FullInfo, workbookElement: W, rendering: A) extends HtmlAppElement {
  override def getDomElement(): Element = rendering.getDomElement()
}

object HtmlWorkbookElement {

  // Helper



}