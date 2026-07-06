package it.evadid.homepage.workbook.htmlRenderer.structureRenderer

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.controlElements.*
import it.evadid.homepage.workbook.htmlRenderer.{HtmlRenderFactory, HtmlWorkbookElement}
import it.evadid.workbook.model.elements.{Workbook, WorkbookSection}

object HtmlWorkbookRenderer extends HtmlRenderFactory[Workbook] {

  override def renderAppElement(workbook: Workbook): HtmlWorkbookElement[Workbook, HtmlAppElement] = {
    val dom = div(
      cls := "it/evadid/homepage/workbook",
      WorkbookHeader(workbook).getDomElement,
      createDomBody(workbook)
    )
    HtmlWorkbookElement[Workbook, HtmlAppElement](fullInfo, workbook, HtmlAppElement(dom))
  }


  /*
  BODY
   */

  private def createDomBody(workbook: Workbook): Element = div(
    cls := "workbook-body",
    children <-- fullInfo.signals.activeSection.map(sectionContainer(workbook, _))
  )

  private def createDomNoSectionActivePlaceholder(): Element = span(
    text <-- contentIdStringSignal(LanguageMapContentId("basic/noSectionSelected")),
  )

  private def createDomSectionContent(workbookSection: WorkbookSection): List[Element] =
    workbookSection.sectionContent.map(HtmlRenderFactory.render).map(_.getDomElement())

  private def sectionContainer(workbook: Workbook, currentlyActiveSection: Option[WorkbookSection]): List[Element] =
    currentlyActiveSection.map(s => createDomSectionContent(s) ++ createDomSectionNavigation(workbook, s)).getOrElse(List(createDomNoSectionActivePlaceholder()))

  private def createDomSectionNavigation(workbook: Workbook, currentSection: WorkbookSection): List[Element] = {
    val sectionIndex = workbook.sections.indexOf(currentSection)
    if (sectionIndex < 0) return List()

    val prevSection: Option[WorkbookSection] = if (sectionIndex > 0) Some(workbook.sections(sectionIndex - 1)) else None
    val nextSection: Option[WorkbookSection] = if (sectionIndex < workbook.sections.size - 1) Some(workbook.sections(sectionIndex + 1)) else None

    List(
      div(
        cls := "section-navigation",
        prevSection match {
          case Some(prev) => button(
            cls := "section-nav-btn section-nav-prev",
            text <-- contentIdStringSignal(LanguageMapContentId("basic/previousSection")),
            onClick --> { _ => fullInfo.control.updateWorkbookConfig(_.copy(activeSection = Some(prev))) }
          )
          case None => emptyNode
        },
        nextSection match {
          case Some(next) => button(
            cls := "section-nav-btn section-nav-next",
            text <-- contentIdStringSignal(LanguageMapContentId("basic/nextSection")),
            onClick --> { _ => fullInfo.control.updateWorkbookConfig(_.copy(activeSection = Some(next))) }
          )
          case None => emptyNode
        }
      )
    )
  }
}






