package workbook.model

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.core.language.{HumanLanguage, LanguageMap}
import workbook.htmlElements.headerElements.HtmlWorkbookHeader
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

import scala.concurrent.ExecutionContext

case class Workbook(
                     workbookInfo: AllWorkbookInfo,
                     title: LanguageMap[HumanLanguage],
                     sections: List[WorkbookSection],
                     titleLanguageMapId: Option[String] = None
                   ) extends HtmlWorkbookElement {

  private def getElement(activeSection: Option[WorkbookSection]): Element =
    if (activeSection.isEmpty) {
      span(
        text <-- workbookInfo.stringSignalFromLanguageMapId("basic/noSectionSelected")(ExecutionContext.global),
      )
    } else {
      activeSection.get.getDomElement()
    }

  private val titleLine = HtmlWorkbookHeader(workbookInfo, title, sections, titleLanguageMapId)

  override def getDomElement(): L.Element = L.div(
    cls := "workbook",
    div(
      cls := "workbook-header",
      titleLine.getDomElement(),
    ),
    div(
      cls := "workbook-body",
      child <-- workbookInfoVar.signal.mapLazy(curInfo => getElement(curInfo.config.activeSection))
    )

  )

}

object Workbook {
  def apply(workbookInfo: AllWorkbookInfo, titleLanguageMapId: String, sections: List[WorkbookSection]): Workbook = {
    Workbook(workbookInfo, LanguageMap.mapBasedLanguageMap(Map.empty[HumanLanguage, String]), sections, Some(titleLanguageMapId))
  }
}
