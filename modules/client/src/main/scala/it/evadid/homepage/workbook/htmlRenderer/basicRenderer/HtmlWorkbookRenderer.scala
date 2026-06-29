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
    val collapsed: Var[Boolean] = Var(false)
    div(
      cls := "it/evadid/homepage/workbook",
      createDomHeader(workbook, collapsed),
      createDomBody(workbook)
    )
  }

  /*
  Header
   */
  def createDomHeader(workbook: Workbook, collapsed: Var[Boolean]): Element = div(
    cls := "workbook-header",
    div(
      cls <-- collapsed.signal.map(c => if (c) "workbook-header-collapsible workbook-header-collapsed" else "workbook-header-collapsible"),
      createDomHeaderTitleLine(workbook),
      UserConfigLine(workbook).getDomElement(),
      LanguageSelectionLine(workbook).getDomElement(),
      SectionSelectionLine(workbook).getDomElement()
    ),
    createDomToggleButton(collapsed)
  )

  private def createDomToggleButton(collapsed: Var[Boolean]): Element = div(
    cls := "workbook-header-toggle",
    onClick --> { _ => collapsed.update(!_) },
    span(
      cls := "workbook-header-toggle-icon",
      child <-- collapsed.signal.map { c =>
        if (c) span("Navigation anzeigen") else span("Navigation ausblenden")
      }
    )
  )

  private def createDomHeaderTitleLine(workbook: Workbook): Element = div(
    cls := "workbook-title-line",
    h1(text <-- contentIdStringSignal(workbook.workbookTitle)),
    UserDropdownMenu().getDomElement()
  )

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


private case class SectionSelectionLine(workbook: Workbook) extends HtmlAppElement {

  private def sections: List[WorkbookSection] = workbook.sections

  private def selectSection(section: WorkbookSection): Unit = {
    fullInfo.control.updateWorkbookConfig(_.copy(activeSection = Some(section)))
  }

  private def isSectionActiveSignal(section: WorkbookSection): Signal[Boolean] = {
    fullInfo.signals.workbook.map(allWorkbookInfo => {
      allWorkbookInfo.exists(curInfo => curInfo.config.activeSection.contains(section))
    })
  }

  private def sectionToElement(section: WorkbookSection): Element = {
    div(
      cls <-- isSectionActiveSignal(section).map(isSectionShowing => if (isSectionShowing) {
        "section-block active"
      } else {
        "section-block"
      }),
      div(
        text <-- fullInfo.signals.stringFromLanguageMapId(section.sectionTitle)
      ),
      onClick --> { event => selectSection(section) },
    )
  }

  override def getDomElement(): L.Element = div(
    cls := "section-overview",
    children <-- Var(sections.map(sectionToElement)).signal
  )
}



private case class UserConfigLine(workbook: Workbook) extends HtmlAppElement {

  //private val resetButton = HtmlButtonElement.withTextLabel(LanguageMapContentId("basic/resetLocalStorage"), event => fullInfo.control.resetLocalStorage())
  private val downloadDataButton = HtmlButtonElement.withTextLabel(LanguageMapContentId("basic/downloadEverything"), event => fullInfo.control.downloadAllAvailableData())
  private val uploadButton = HtmlButtonElement.withTextLabel(LanguageMapContentId("basic/uploadSession"), event => uploadInput.ref.click())

  private lazy val uploadInput: ReactiveHtmlElement[HTMLInputElement] = input(
    styleAttr := "display:none;",
    typ := "file",
    accept := "json",
    onChange --> { event =>
      val inputElement = event.target.asInstanceOf[dom.html.Input]
      if (inputElement.files.length > 0) fileToUploadSelected(inputElement.files.item(0))
    }
  )

  private def fileToUploadSelected(file: File): Unit = {
    fullInfo.current.workbookUserData.foreach(_.upload(FileFactory.fromFile(file)))
  }

  private val domElement: Element = div(
    styleAttr := "display:none;",
    uploadInput,
    //  resetButton.getDomElement(),
    downloadDataButton.getDomElement(),
    uploadButton.getDomElement(),
  )

  override def getDomElement(): Element = domElement
}


