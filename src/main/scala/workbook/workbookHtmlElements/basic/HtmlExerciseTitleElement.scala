package workbook.workbookHtmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlExerciseTitleElement(languageMap: LanguageMap[HumanLanguage]) extends HtmlWorkbookElement {

  override def getDomElement(): L.Element = div(
    cls := "exercise-title",
    languageMap.getInLanguage(AppLanguage.English)
  )

}
