package it.evadid.homepage.workbook.htmlRenderer.basicRenderer

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.elements.CollapsibleInstructionElement

object HtmlCollapsibleInstructionRenderer extends HtmlRenderFactory[CollapsibleInstructionElement] {

  override protected def createDomElement(workbookElement: CollapsibleInstructionElement): Element = {
    val collapsedVar = Var(workbookElement.initiallyCollapsed)

    val toggleIconSignal: Signal[String] = collapsedVar.signal.map {
      case true  => "+"
      case false => "−"
    }

    val bodyClsSignal: Signal[String] = collapsedVar.signal.map {
      case true  => "collapsible-body collapsible-body--collapsed"
      case false => "collapsible-body collapsible-body--expanded"
    }

    div(
      cls := "workbook-element exercise-instruction collapsible-instruction",
      div(
        cls := "collapsible-header",
        onClick --> { _ => collapsedVar.update(!_) },
        span(
          cls := "collapsible-toggle-icon",
          child.text <-- toggleIconSignal
        ),
        span(
          cls := "collapsible-title",
          text <-- contentIdStringSignal(workbookElement.titleLabel)
        )
      ),
      div(
        cls := "collapsible-body",
        cls <-- bodyClsSignal,
        child <-- contentIdStringSignal(workbookElement.bodyContent).map { text =>
          div(
            cls := "collapsible-body__text",
            text
          )
        }
      )
    )
  }
}