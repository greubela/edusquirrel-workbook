package workbook.workbookHtmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class HtmlPlaintextInstructionElement(workbookInfoVar: Var[WorkbookInfo], languageMap: LanguageMap[HumanLanguage]) extends HtmlWorkbookElement {

  override def getDomElement(): L.Element = div(
    cls := "workbook-element exercise-instruction",
    div(cls := "instruction-content",
      child <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(languageMap.getInLanguage)
    )
  )

}
