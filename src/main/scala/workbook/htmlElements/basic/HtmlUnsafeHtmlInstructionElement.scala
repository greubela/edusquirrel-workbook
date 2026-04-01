package workbook.htmlElements.basic

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import datastructures.core.language.{HumanLanguage, LanguageMap}
import datastructures.web.storage.AsyncDataCache
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}

import scala.concurrent.ExecutionContext

case class HtmlUnsafeHtmlInstructionElement(workbookInfo: AllWorkbookInfo, labelSignal: Signal[String]) extends HtmlWorkbookElement {
  
  override def getDomElement(): Element = div(
    cls := "workbook-element exercise-instruction",
    child <-- labelSignal.map { html =>
      foreignHtmlElement(DomApi.unsafeParseHtmlString("<div class=\"instruction-content\">" + html + "</div>"))
    }
  )
  
}

object HtmlUnsafeHtmlInstructionElement {
  
  def apply(workbookInfo: AllWorkbookInfo, languageMapId: String): HtmlUnsafeHtmlInstructionElement = {
    val signal: Signal[String] = workbookInfo.stringSignalFromLanguageMapId(languageMapId)(ExecutionContext.global)
    HtmlUnsafeHtmlInstructionElement(workbookInfo, signal)
  }
  
}

