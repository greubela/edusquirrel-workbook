package workbook.workbookHtmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.AppLanguage
import workbook.workbookHtmlElements.abstractions.HtmlLanguageMapElement

class HtmlExerciseTitleElement(languageMap: Map[AppLanguage, String]) extends HtmlLanguageMapElement(languageMap) {

  override def getDomElement(): L.Element = div(
    cls := "exercise-title",
    getContentAsString()
  )

}
