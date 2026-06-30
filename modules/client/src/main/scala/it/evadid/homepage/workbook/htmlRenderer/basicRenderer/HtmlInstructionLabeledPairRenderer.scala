package it.evadid.homepage.workbook.htmlRenderer.basicRenderer

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import it.evadid.core.util.MarkdownToHtml
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.elements.LabeledInstructionElement

object HtmlInstructionLabeledPairRenderer extends HtmlRenderFactory[LabeledInstructionElement] {

  /*override protected def createDomElement(workbookElement: LabeledInstructionElement): Element = div(
    cls := "workbook-element exercise-instruction ${workbookElement.labelType.associatedCssString}",
    div(
      cls := s"${workbookElement.labelType.associatedCssString}__title",
      text <-- super.contentIdStringSignal(workbookElement.titleLable)
    ),
    div(
      cls := s"${workbookElement.labelType.associatedCssString}__body",
      text <-- super.contentIdStringSignal(workbookElement.bodyContent)
    )
  )*/

  override protected def createDomElement(workbookElement: LabeledInstructionElement): Element = {
    val cssLabel = workbookElement.labelType.associatedCssString
    div(
      cls := s"workbook-element exercise-instruction $cssLabel",
      div(
        cls := s"${cssLabel}__title",
        text <-- super.contentIdStringSignal(workbookElement.titleLable)
      ),
      div(
        cls := s"${cssLabel}__body",
        child <-- super.contentIdStringSignal(workbookElement.bodyContent).map { text =>
          val html: String = MarkdownToHtml.transform(text)
          foreignHtmlElement(DomApi.unsafeParseHtmlString(s"<div class=\"${cssLabel}__body--html\">$html</div>"))
        }
      )
    )
  }

}

/*
object HtmlInstructionLabeledPairRenderer extends HtmlRenderFactory[LabeledInstructionElement] {



}*/
