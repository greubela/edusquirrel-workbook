package workbook.htmlElements.basic

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import it.evadid.core.util.MarkdownToHtml
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo
case class HtmlInstructionElement(fullInfo: FullInfo, languageMapId: String, childSignal: Signal[Element]) extends HtmlWorkbookElement {


  override def getDomElement(): Element = div(
    cls := "workbook-element exercise-instruction",
    //     cls := "exercise-markdown-instruction",
    child <-- childSignal
  )


}

object HtmlInstructionElement {

  private def fromLanguageMapId(fullInfo: FullInfo, languageMapId: String, contentToElement: String => Element = div(_)): HtmlInstructionElement = {
    //val languageMapSignal = homepageInfo.technicalElements.languageMapStorage.loadIntoVariable(languageMapId)(ExecutionContext.global).signal
    val contentSignal = fullInfo.signals.stringFromLanguageMapId(languageMapId)
    HtmlInstructionElement(fullInfo, languageMapId, contentSignal.map(contentToElement))//, languageMapSignal)
  }
  
  private def plaintextToElement(text: String): Element = div(cls := "instruction-content", text)
  private def unsafeHtmlToElement(html: String): Element = foreignHtmlElement(DomApi.unsafeParseHtmlString(s"<div class=\"instruction-content\">${html}</div>"))
  private def markdownToElement(markdownString: String): Element = {
    val markdownHtml = MarkdownToHtml.transform(markdownString)
    unsafeHtmlToElement(s"<div class=\"instruction-content markdown-content\">${markdownHtml}</div>")
  }
  
  def fromMarkdownLanguageMapId(fullInfo: FullInfo, languageMapId: String): HtmlInstructionElement = {
    fromLanguageMapId(fullInfo, languageMapId, markdownToElement)
  }

  def fromUnsafeHtmlLanguageMapId(fullInfo: FullInfo, languageMapId: String): HtmlInstructionElement = {
    fromLanguageMapId(fullInfo, languageMapId, unsafeHtmlToElement)
  }

  def fromPlaintextLanguageMapId(fullInfo: FullInfo, languageMapId: String): HtmlInstructionElement = {
    fromLanguageMapId(fullInfo, languageMapId, plaintextToElement)
  }
  

}
