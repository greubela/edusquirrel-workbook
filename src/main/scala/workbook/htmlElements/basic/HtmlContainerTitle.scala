package workbook.htmlElements.basic

import com.raquo.laminar.api.L.*
import workbook.model.abstractions.*
import workbook.model.info.FullInfo

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
      text <-- fullInfo.signals.stringFromLanguageMapId(languageMapId)
    )
  }

}

