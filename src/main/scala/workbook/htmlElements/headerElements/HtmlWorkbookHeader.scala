package workbook.htmlElements.headerElements

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import datastructures.core.language.{AppLanguage, HumanLanguage, LanguageMap}
import workbook.model.WorkbookSection
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}
import workbook.htmlElements.headerElements.LanguageSelectionLine

case class HtmlWorkbookHeader(workbookInfo: AllWorkbookInfo, workbookTitle: LanguageMap[HumanLanguage], sections: List[WorkbookSection]) extends HtmlWorkbookElement {

  private val languageLine: LanguageSelectionLine = LanguageSelectionLine(workbookInfo)
  private val sectionLine: SectionSelectionLine = SectionSelectionLine(workbookInfo, sections)
  
  private val domElement: Element = div(
    cls := "workbook-title-line",
    h1(
      text <-- workbookInfo.stringSignalFromLanguageMap(workbookTitle),
    ),
    languageLine.getDomElement(),
    sectionLine.getDomElement()
  )

  override def getDomElement(): Element = domElement


}

object HtmlWorkbookHeader {

}
