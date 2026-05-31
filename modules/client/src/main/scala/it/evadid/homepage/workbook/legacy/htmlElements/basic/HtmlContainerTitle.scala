package it.evadid.homepage.workbook.legacy.htmlElements.basic

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.legacy.model.abstractions.HtmlWorkbookElement
import it.evadid.homepage.workbook.legacy.model.info.FullInfo
import it.evadid.homepage.workbook.legacy.model.abstractions.*

import scala.concurrent.ExecutionContext

case class HtmlContainerTitle(fullInfo: FullInfo, languageMapId: String, level: Int = 2) extends HtmlWorkbookElement {

  private def normalizedLevel: Int = math.max(1, math.min(6, level))

  private def headingElement(content: String): Element = normalizedLevel match {
    case 1 => h1(content)
    case 2 => h2(content)
    case 3 => h3(content)
    case 4 => h4(content)
    case 5 => h5(content)
    case _ => h6(content)
  }

  override def getDomElement(): Element = {
    div(
      cls := "workbook-element container-title",
      cls := s"container-title-level-$normalizedLevel",
      child <-- fullInfo.signals.stringFromLanguageMapId(languageMapId).map(headingElement)
    )
  }

}

