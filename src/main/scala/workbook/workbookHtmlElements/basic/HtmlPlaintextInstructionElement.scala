package workbook.workbookHtmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.AppLanguage
import workbook.workbookHtmlElements.abstractions.HtmlLanguageMapElement

case class HtmlPlaintextInstructionElement(languageMap: Map[AppLanguage, String]) extends HtmlLanguageMapElement(languageMap) {

  override def getDomElement(): L.Element = div(
    cls := "container-exercise-instruction",
    div(cls := "instruction-content",
    getContentAsString()
    )
  )

}
