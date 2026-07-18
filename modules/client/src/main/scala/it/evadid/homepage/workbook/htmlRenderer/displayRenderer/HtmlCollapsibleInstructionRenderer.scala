package it.evadid.homepage.workbook.htmlRenderer.displayRenderer

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.displayElements.CollapsibleInstructionElement

object HtmlCollapsibleInstructionRenderer extends LineBasedRenderingFactory[CollapsibleInstructionElement] {

  override protected def createRendering(workbookElement: CollapsibleInstructionElement): AtomarLineRendering = {
    val collapsedVar = Var(workbookElement.initiallyCollapsed)

    val toggleIconSignal: Signal[String] = collapsedVar.signal.map {
      case true  => "+"
      case false => "\u2212"
    }

    val bodyClsSignal: Signal[String] = collapsedVar.signal.map {
      case true  => "collapsible-body collapsible-body--collapsed"
      case false => "collapsible-body collapsible-body--expanded"
    }

    val dom: Element =
      div(
        cls := "collapsible-instruction",
        div(
          cls := "collapsible-header",
          onClick --> { _ => collapsedVar.update(!_) },
          span(
            cls := "collapsible-toggle-icon",
            child.text <-- toggleIconSignal
          ),
          span(
            cls := "collapsible-title",
            text <-- laminarHelper.plaintextStringSignal(workbookElement.titleLabel)
          )
        ),
        div(
          cls <-- bodyClsSignal,
          child <-- laminarHelper.plaintextStringSignal(workbookElement.bodyContent).map { bodyText =>
            div(
              cls := "collapsible-body__text",
              bodyText
            )
          }
        )
      )

    AtomarLineRendering.basicLine(workbookElement, dom, "collapsible-instruction")
  }
}
