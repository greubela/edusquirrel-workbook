package it.evadid.homepage.workbook.htmlRenderer.basicRenderer

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.MarkdownToHtml
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, RenderingAsTitle, RenderingLine}
import it.evadid.workbook.model.abstractions.*
import it.evadid.workbook.model.abstractions.RoleInWorkbook.*
import it.evadid.workbook.model.abstractions.TypeOfTextContent.*
import it.evadid.workbook.model.elements.LangMapContentBasedElement

object HtmlLangMapContentRenderer extends LineBasedRenderingFactory[LangMapContentBasedElement] {

  override protected def createRendering(workbookElement: LangMapContentBasedElement): AtomarLineRendering = {
    val contentId: LanguageMapContentId = workbookElement.content

    workbookElement.contentType match {
      case LangMapContentIdType(EXERCISE_DESCRIPTION, textType) =>
        instructionElement(contentId, textType)
      case LangMapContentIdType(CONTAINER_TITLE, PLAINTEXT) =>
        val dom: Element = span(text <-- contentIdStringSignal(contentId))
        RenderingAsTitle(dom, 1)
      case _ => placeholder("LangMapContentRenderer::createRendering cannot yet handle objects of type '" + workbookElement.contentType + "'!")
    }
  }

  private def instructionElement(contentId: LanguageMapContentId, typeOfTextContent: TypeOfTextContent): AtomarLineRendering = {
    def instructionPlaintextToElement(text: String): Element = {
      div(cls := "instruction-content", text)
    }

    def instructionUnsafeHtmlToElement(html: String): Element = {
      foreignHtmlElement(DomApi.unsafeParseHtmlString(s"<div class=\"instruction-content\">${html}</div>"))
    }

    def instructionMarkdownToElement(markdownString: String): Element = {
      val markdownHtml = MarkdownToHtml.transform(markdownString)
      instructionUnsafeHtmlToElement(s"<div class=\"instruction-content markdown-content\">${markdownHtml}</div>")
    }

    val transformFunction: String => Element = typeOfTextContent.match {
      case PLAINTEXT => instructionPlaintextToElement
      case MARKDOWN => instructionMarkdownToElement
      case HTML => instructionUnsafeHtmlToElement
      case _ => instructionPlaintextToElement
    }

    val signal: Signal[List[Element]] = contentIdStringSignal(contentId).map(transformFunction).map(List(_))

    RenderingLine(false, signal, "")
  }


}


