package workbook.workbookHtmlElements.abstractions

import com.raquo.laminar.api.L
import contentmanagement.model.language.AppLanguage

abstract class HtmlLanguageMapElement(languageMap: Map[AppLanguage, String]) extends HtmlWorkbookElement {

  private var curLanguage: AppLanguage = languageMap.keys.head

  def setLanguage(language: AppLanguage): Unit = curLanguage = language

  protected def getContentAsString(): String = if (languageMap.contains(curLanguage)) {
    languageMap(curLanguage)
  } else {
    "[CONTENT NOT AVAILABLE IN '" + curLanguage + "', showing 'ENGLISH' instead]:\n" + languageMap(AppLanguage.English)
  }

}
