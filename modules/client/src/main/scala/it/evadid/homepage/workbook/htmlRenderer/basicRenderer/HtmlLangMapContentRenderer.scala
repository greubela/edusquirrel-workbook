package it.evadid.homepage.workbook.htmlRenderer.basicRenderer

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.MarkdownToHtml
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.abstractions.*
import it.evadid.workbook.model.abstractions.RoleInWorkbook.*
import it.evadid.workbook.model.abstractions.TypeOfTextContent.*
import it.evadid.workbook.model.elements.LangMapContentBasedElement

object HtmlLangMapContentRenderer extends HtmlRenderFactory[LangMapContentBasedElement] {

  override protected def createDomElement(workbookElement: LangMapContentBasedElement): Element = {
    val contentId: LanguageMapContentId = workbookElement.content

    workbookElement.contentType match {
      case LangMapContentIdType(EXERCISE_DESCRIPTION, textType) => instructionElement(contentId, textType)
      case LangMapContentIdType(CONTAINER_TITLE, PLAINTEXT) => HtmlContainerTitle(contentId, level = 1).getDomElement()
      case _ => placeholder("LangMapContentRenderer::createDomElement cannot yet render objects of type '" + workbookElement.contentType + "'!")
    }
  }

  private def instructionElement(contentId: LanguageMapContentId, typeOfTextContent: TypeOfTextContent): Element = {
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
    div(
      cls := "workbook-element exercise-instruction",
      child <-- contentIdStringSignal(contentId).map(transformFunction)
    )
  }


  private case class HtmlContainerTitle(contentId: LanguageMapContentId, level: Int = 2) extends HtmlAppElement {

    private def normalizedLevel: Int = math.max(1, math.min(6, level))

    private def headingElement(content: String): Element = normalizedLevel match {
      case 1 => h1(content)
      case 2 => h2(content)
      case 3 => h3(content)
      case 4 => h4(content)
      case 5 => h5(content)
      case _ => h6(content)
    }

    override def getDomElement(): Element = {

      div(
        cls := "workbook-element container-title",
        cls := s"container-title-level-$normalizedLevel",
        text <-- contentIdStringSignal(contentId)
      )
    }
  }

}


