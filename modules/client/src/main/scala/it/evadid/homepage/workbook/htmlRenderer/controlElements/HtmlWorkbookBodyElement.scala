package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.homepage.control.model.AllWorkbookInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement.ButtonConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}
import org.scalajs.dom.HTMLDivElement

case class HtmlWorkbookBodyElement(workbook: AllWorkbookInfo, user: AllUserInfo) extends HtmlAppElement {

  //private val collapsed: Var[Boolean] = Var(true)

  val domElement: ReactiveHtmlElement[HTMLDivElement] = div(
    cls := "workbook-body",
    children <-- fullInfo.signals.activeSection.map(sectionContainer(workbook.loadedWorkbook, _))
  )

  override def getDomElement(): L.Element = domElement

  private def createDomSectionContent(workbookSection: WorkbookSection): List[Element] =
    workbookSection.sectionContent.map(HtmlRenderFactory.render).map(_.getDomElement())

  private def sectionContainer(workbook: Workbook, currentlyActiveSection: Option[WorkbookSection]): List[Element] = currentlyActiveSection.match {
    case Some(s) => createDomSectionContent(s) ++ createDomSectionNavigation(workbook, s)
    case None => List(span(text <-- laminarHelper.plaintextStringSignal("basic/noSectionSelected")))
  }

  private def createDomSectionNavigation(workbook: Workbook, currentSection: WorkbookSection): List[Element] = {
    val sectionIndex: Int = workbook.sections.indexOf(currentSection)
    if (sectionIndex < 0) return List()

    val prevSection: Option[WorkbookSection] = if (sectionIndex > 0) Some(workbook.sections(sectionIndex - 1)) else None
    val nextSection: Option[WorkbookSection] = if (sectionIndex < workbook.sections.size - 1) Some(workbook.sections(sectionIndex + 1)) else None

    val buttonPrev: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/previousSection", _ => fullInfo.usageControl.updateWorkbookConfig(_.copy(activeSection = prevSection)), ButtonConfig(prevSection.nonEmpty, List()))
    val buttonNext: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/nextSection", _ => fullInfo.usageControl.updateWorkbookConfig(_.copy(activeSection = nextSection)), ButtonConfig(nextSection.nonEmpty, List()))

    List(
      div(
        cls := "section-navigation",
        buttonPrev.getDomElement(),
        buttonNext.getDomElement()
      )
    )
  }

}
