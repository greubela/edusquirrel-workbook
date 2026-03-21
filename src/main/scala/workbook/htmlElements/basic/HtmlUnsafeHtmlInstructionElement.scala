package workbook.htmlElements.basic

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.WorkbookInfo

case class HtmlUnsafeHtmlInstructionElement(workbookInfoVar: Var[WorkbookInfo], languageMap: LanguageMap[HumanLanguage]) extends HtmlWorkbookElement {

  private val htmlSignal: Signal[String] = {
    workbookInfoVar.signal
      .map(_.config.currentWorkbookLanguage)
      .map(languageMap.getInLanguage)
  }


  override def getDomElement(): Element = div(
    cls := "workbook-element exercise-instruction",
    child <-- htmlSignal.map { html =>
      println("html: " + html)
      foreignHtmlElement(DomApi.unsafeParseHtmlString("<div class=\"instruction-content\">" + html + "</div>"))
    }
  )


}
