package workbook.htmlElements.basic

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.storage.DataStorage
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.WorkbookInfo

case class HtmlUnsafeHtmlInstructionElement(workbookInfoVar: Var[WorkbookInfo], labelSignal: Signal[String]) extends HtmlWorkbookElement {



  override def getDomElement(): Element = div(
    cls := "workbook-element exercise-instruction",
    child <-- labelSignal.map { html =>
      foreignHtmlElement(DomApi.unsafeParseHtmlString("<div class=\"instruction-content\">" + html + "</div>"))
    }
  )
  
}

object HtmlUnsafeHtmlInstructionElement {
  
  def apply(workbookInfoVar: Var[WorkbookInfo], languageMapId: String): HtmlUnsafeHtmlInstructionElement = {
    HtmlUnsafeHtmlInstructionElement(workbookInfoVar, DataStorage.labelSignalFromLanguageMapName(languageMapId, workbookInfoVar))
  }
  
}

