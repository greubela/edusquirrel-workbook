package workbook.htmlElements.basic

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import util.web.MarkdownToHtml
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

import scala.concurrent.ExecutionContext

case class HtmlMarkdownInstructionElement(workbookInfo: AllWorkbookInfo, markdownSignal: Signal[String]) extends HtmlWorkbookElement {

  override def getDomElement(): Element = div(
    cls := "workbook-element exercise-instruction",
    cls := "exercise-markdown-instruction",
    child <-- markdownSignal.map(markdown =>
      foreignHtmlElement(DomApi.unsafeParseHtmlString(s"<div class=\"instruction-content markdown-content\">${MarkdownToHtml.transform(markdown)}</div>"))
    )
  )
}

object HtmlMarkdownInstructionElement {

  def apply(workbookInfo: AllWorkbookInfo, languageMapId: String): HtmlMarkdownInstructionElement = {
    val signal: Signal[String] = workbookInfo.stringSignalFromLanguageMapId(languageMapId)(ExecutionContext.global)
    HtmlMarkdownInstructionElement(workbookInfo, signal)
  }
}
