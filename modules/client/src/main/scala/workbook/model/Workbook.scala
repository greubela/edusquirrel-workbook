package workbook.model

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.core.language.{HumanLanguage, LanguageMap}
import workbook.htmlElements.headerElements.HtmlWorkbookHeader
import workbook.model.abstractions.{HtmlWorkbookElement, WorkbookInteraction}
import workbook.model.info.{AllWorkbookInfo, FullInfo, HomepageInfo}

import scala.collection.mutable
import scala.concurrent.ExecutionContext

case class Workbook(
                     fullInfo: FullInfo,
                     titleLanguageMapId: String,
                     sections: List[WorkbookSection],
                     availableInLanguages: List[HumanLanguage] = List()
                   ) extends HtmlWorkbookElement {

  lazy val allInteractionElements: List[WorkbookInteraction[_]] = {
    val res = mutable.ListBuffer[WorkbookInteraction[_]]()
    sections.foreach(curSection => curSection.sectionContent.foreach(curContainer => curContainer.children.foreach {
      case interaction: WorkbookInteraction[_] => res.append(interaction)
      case other =>
    }))
    res.toList
  }

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
