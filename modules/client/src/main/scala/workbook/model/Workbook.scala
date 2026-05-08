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
  
  private def getElement(activeSection: Option[WorkbookSection]): Element =
    if (activeSection.isEmpty) {
      span(
        text <-- fullInfo.signals.stringFromLanguageMapId("basic/noSectionSelected"),
      )
    } else {
      activeSection.get.getDomElement()
    }

  private val titleLine = HtmlWorkbookHeader(fullInfo, titleLanguageMapId, sections)

  private lazy val sectionContentSignal: Signal[Element] = {
    val activeSectionSignal: Signal[Option[WorkbookSection]] = fullInfo.signals.activeSection
    val noSectionLoadedLabel: Signal[String] = fullInfo.signals.stringFromLanguageMapId("basic/noSectionSelected")

    Signal.combine(activeSectionSignal, noSectionLoadedLabel).map { case (aS, nS) =>
      if (aS.isEmpty) span(nS) else aS.get.getDomElement()
    }
  }

  override def getDomElement(): L.Element = L.div(
    cls := "workbook",
    div(
      cls := "workbook-header",
      titleLine.getDomElement(),
    ),
    div(
      cls := "workbook-body",
      child <-- sectionContentSignal
    )
  )

}
