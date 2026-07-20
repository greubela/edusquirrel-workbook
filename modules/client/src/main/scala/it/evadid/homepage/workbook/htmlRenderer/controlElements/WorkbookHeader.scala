package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.elements.structureElements.Workbook
import org.scalajs.dom.HTMLDivElement

case class WorkbookHeader(workbook: Workbook) extends HtmlAppElement {

  //private val collapsed: Var[Boolean] = Var(true)

  private def collapsedSignal: Signal[Boolean] = fullInfo.signals.display.map(_.collapsedNavigation)

  def getDomElement(): Element = domElement

  private lazy val domElement: ReactiveHtmlElement[HTMLDivElement] = div(
    cls := "workbook-header",
    children <-- domChildren
  )

  lazy val domChildren: Signal[List[Element]] = collapsedSignal.map((isCollapsed: Boolean) => {
    if (isCollapsed) {
      List(
        createDomHeaderTitleLine(workbook),
        UserDropdownMenu().getDomElement(),
        createDomToggleButton()
      )
    } else List(
      createDomHeaderTitleLine(workbook),
      UserDropdownMenu().getDomElement(),
      LanguageSelectionLine(workbook).getDomElement(),
      SectionSelectionLine(workbook).getDomElement(),
      createDomToggleButton()
    )
  })

  private def createDomToggleButton(): Element = div(
    cls := "workbook-header-toggle",
    onClick --> { _ => fullInfo.usageControl.changeDisplay(displayInfo => displayInfo.copy(collapsedNavigation = !displayInfo.collapsedNavigation)) },
    span(
      child <-- collapsedSignal.map { c =>
        if (c) span(text <-- laminarHelper.plaintextStringSignal("basic/showHeader"))
        else span(text <-- laminarHelper.plaintextStringSignal("basic/hideHeader"))
      }
    )
  )

  private def createDomHeaderTitleLine(workbook: Workbook): Element = div(
    cls := "workbook-title-line",
    h1(text <-- laminarHelper.plaintextStringSignal(workbook.workbookTitle)),

  )


}
