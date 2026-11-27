package it.evadid.homepage.workbook.htmlRenderer.displayRenderer

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.MarkdownToHtml
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.abstractions.RoleInWorkbook.EXERCISE_DESCRIPTION
import it.evadid.workbook.abstractions.TypeOfTextDisplay.{HTML, MARKDOWN, PLAINTEXT}
import it.evadid.workbook.abstractions.{LangMapContentIdType, TypeOfTextDisplay, WorkbookElement}
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent

object HtmlDisplayLangMapContentRenderer extends LineBasedRenderingFactory[DisplayLangMapContent] {

  override protected def createRendering(fullInfo: FullInfo, workbookElement: DisplayLangMapContent): AtomarLineRendering = {
    val contentId: LanguageMapContentId = workbookElement.content

    workbookElement.contentType match {
      case LangMapContentIdType(EXERCISE_DESCRIPTION, textType) =>
        instructionElement(fullInfo, workbookElement, contentId, textType)
      //case LangMapContentIdType(CONTAINER_TITLE, PLAINTEXT) =>        AtomarLineRendering.exerciseContainerTitleLine(contentId)
      case _ => placeholder(fullInfo, workbookElement, "LangMapContentRenderer::createRendering cannot yet handle objects of type '" + workbookElement.contentType + "'!")
    }
  }

  private def instructionElement(fullInfo: FullInfo, workbookElement: WorkbookElement, contentId: LanguageMapContentId, typeOfTextContent: TypeOfTextDisplay): AtomarLineRendering = {
    def instructionPlaintextToElement(text: String): Element = {
      div(cls := "instruction-content", text)
    }

    def instructionUnsafeHtmlToElement(html: String): Element = {
      foreignHtmlElement(DomApi.unsafeParseHtmlString(s"<div class=\"instruction-content\">${html}</div>"))
    }

    def instructionMarkdownToElement(markdownString: String): Element = {
      val markdownHtml = MarkdownToHtml.transform(markdownString)
      foreignHtmlElement(DomApi.unsafeParseHtmlString(
        s"<div class=\"instruction-content markdown-content\">${markdownHtml}</div>"))
    }

    val transformFunction: String => Element = typeOfTextContent.match {
      case PLAINTEXT => instructionPlaintextToElement
      case MARKDOWN => instructionMarkdownToElement
      case HTML => instructionUnsafeHtmlToElement
      case _ => instructionPlaintextToElement
    }

    val signal: Signal[Seq[Element]] = laminarHelper(fullInfo).plaintextStringSignal(contentId).map(transformFunction).map(List(_))

    AtomarLineRendering.basicLine(fullInfo, workbookElement, signal)
  }


}


