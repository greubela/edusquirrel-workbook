package it.evadid.homepage.workbook.htmlRenderer.basicRenderer

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.{HtmlButtonElement, HtmlDropdownMenu}
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.controlElements.*
import it.evadid.workbook.model.elements.{Workbook, WorkbookSection}
import org.scalajs.dom
import org.scalajs.dom.{File, HTMLInputElement}
import todomove.datastructures.web.file.FileFactory

object HtmlWorkbookRenderer extends HtmlRenderFactory[Workbook] {

  def createDomElement(workbook: Workbook): Element = {
    div(
      cls := "it/evadid/homepage/workbook",
      WorkbookHeader(workbook).getDomElement,
      createDomBody(workbook)
    )
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
    workbookSection.sectionContent.map(HtmlRenderFactory.renderWorkbookElement).map(_.getDomElement())

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






