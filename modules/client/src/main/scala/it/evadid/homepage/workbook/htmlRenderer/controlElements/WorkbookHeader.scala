package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.homepage.workbook.htmlRenderer.basicRenderer.HtmlWorkbookRenderer.contentIdStringSignal
import it.evadid.workbook.model.elements.Workbook
import org.scalajs.dom.HTMLDivElement

case class WorkbookHeader(workbook: Workbook) {

  private val collapsed: Var[Boolean] = Var(false)

  def getDomElement: Element = domElement

  private lazy val domElement: ReactiveHtmlElement[HTMLDivElement] = div(
    cls := "workbook-header",
    div(
      cls <-- collapsed.signal.map(c => if (c) "workbook-header-collapsible workbook-header-collapsed" else "workbook-header-collapsible"),
      createDomHeaderTitleLine(workbook),
      UserContextControlLine(workbook).getDomElement(),
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


}
