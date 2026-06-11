package it.evadid.homepage.workbook.htmlRenderer.basicRenderer

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.abstractions.{WorkbookElement, WorkbookElementGroup}
import it.evadid.workbook.model.elements.LabeledInstructionElement

object HtmlInstructionLabeledPairRenderer extends HtmlRenderFactory[LabeledInstructionElement] {

  override protected def createDomElement(workbookElement: LabeledInstructionElement): Element = div(
    cls := "workbook-element exercise-instruction ${workbookElement.labelType.associatedCssString}",
    div(
      cls := s"${workbookElement.labelType.associatedCssString}__title",
      text <-- super.contentIdStringSignal(workbookElement.titleLable)
    ),
    div(
      cls := s"${workbookElement.labelType.associatedCssString}__body",
      text <-- super.contentIdStringSignal(workbookElement.bodyContent)
    )
  )

}
