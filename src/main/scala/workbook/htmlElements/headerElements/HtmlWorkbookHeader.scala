package workbook.htmlElements.headerElements

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import workbook.model.WorkbookSection
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.WorkbookInfo
import workbook.htmlElements.headerElements.LanguageSelectionLine

case class HtmlWorkbookHeader(workbookInfoVar: Var[WorkbookInfo], workbookTitle: LanguageMap[HumanLanguage], sections: List[WorkbookSection]) extends HtmlWorkbookElement {

  private val languageLine: LanguageSelectionLine = LanguageSelectionLine(workbookInfoVar)
  private val sectionLine: SectionSelectionLine = SectionSelectionLine(workbookInfoVar, sections)
  
  private val domElement: Element = div(
    cls := "workbook-title-line",
    h1(
      child <-- workbookInfoVar.signal.map(_.languageStringFromMap(workbookTitle)),
    ),
    languageLine.getDomElement(),
    sectionLine.getDomElement()
  )

  override def getDomElement(): Element = domElement


}

object HtmlWorkbookHeader {

}
