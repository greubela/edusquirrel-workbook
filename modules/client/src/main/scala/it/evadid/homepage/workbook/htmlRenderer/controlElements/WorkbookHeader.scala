package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.homepage.workbook.htmlRenderer.basicRenderer.HtmlWorkbookRenderer.contentIdStringSignal
import it.evadid.workbook.model.elements.Workbook
import org.scalajs.dom.HTMLDivElement

case class WorkbookHeader(workbook: Workbook) extends ControlFactory {

  private val collapsed: Var[Boolean] = Var(false)

  def getDomElement: Element = domElement

  private lazy val domElement: ReactiveHtmlElement[HTMLDivElement] =     div(
      cls := "workbook-header",
      children <-- domChildren
    )

  lazy val domChildren: Signal[List[Element]] = collapsed.signal.mapLazy(curValue => {
    if (curValue) {
      List(createDomToggleButton(collapsed))
    } else List(
      createDomHeaderTitleLine(workbook),
      UserContextControlLine(workbook).getDomElement(),
      LanguageSelectionLine(workbook).getDomElement(),
      SectionSelectionLine(workbook).getDomElement(),
      createDomToggleButton(collapsed)
    )
  })

  private def createDomToggleButton(collapsed: Var[Boolean]): Element = div(
    cls := "workbook-header-toggle",
    span(
      cls := "workbook-header-toggle-icon",
      onClick --> { _ => collapsed.update(!_) },
      child <-- collapsed.signal.map { c =>
        if (c) span(text <-- labelString("basic/showHeader"))
        else span(text <-- labelString("basic/hideHeader"))
      }
    )
  )

  private def createDomHeaderTitleLine(workbook: Workbook): Element = div(
    cls := "workbook-title-line",
    h1(text <-- contentIdStringSignal(workbook.workbookTitle)),
    UserDropdownMenu().getDomElement()
  )


}
