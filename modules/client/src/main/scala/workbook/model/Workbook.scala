package workbook.model

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.*
import workbook.htmlElements.headerElements.HtmlWorkbookHeader
import workbook.model.abstractions.{HtmlWorkbookElement, WorkbookInteraction}
import workbook.model.info.FullInfo

import scala.collection.mutable

case class Workbook(
                     fullInfo: FullInfo,
                     titleLanguageMapId: String,
                     sections: List[WorkbookSection],
                     availableInLanguages: List[HumanLanguage] = List()
                   ) extends HtmlWorkbookElement {

  override lazy val workbookChildren: List[HtmlWorkbookElement] = sections

  private val titleLine = HtmlWorkbookHeader(fullInfo, titleLanguageMapId, sections)

  private def sectionWrapper(section: WorkbookSection): Element = {
    div(
      cls := "workbook-section",
      cls.toggle("workbook-section--hidden") <-- fullInfo.signals.activeSection.map(_.forall(_ != section)),
      section.getDomElement()
    )
  }

  private lazy val domElement: L.Element = L.div(
    cls := "workbook",
    div(
      cls := "workbook-header",
      titleLine.getDomElement(),
    ),
    div(
      cls := "workbook-body",
      sections.map(sectionWrapper)
    )
  )

  override def getDomElement(): L.Element = domElement

}
