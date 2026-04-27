package workbook.htmlElements.headerElements

import com.raquo.laminar.api.L.*
import datastructures.core.language.{HumanLanguage, LanguageMap}
import org.scalajs.dom.console
import workbook.htmlElements.basic.HtmlButtonElement
import workbook.model.WorkbookSection
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo

import scala.concurrent.ExecutionContext

case class HtmlWorkbookHeader(
                               fullInfo: FullInfo,
                               workbookTitleId: String,
                               sections: List[WorkbookSection],
                             ) extends HtmlWorkbookElement {

  private val languageLine: LanguageSelectionLine = LanguageSelectionLine(fullInfo)
  private val sectionLine: SectionSelectionLine = SectionSelectionLine(fullInfo, sections)
  private val userConfigLine: UserConfigLine = UserConfigLine(fullInfo)

  private val domElement: Element = div(
    cls := "workbook-title-line",
    h1(
      text <-- fullInfo.signals.stringFromLanguageMapId(workbookTitleId),
    ),
    userConfigLine.getDomElement(),
    languageLine.getDomElement(),
    sectionLine.getDomElement()
  )

  override def getDomElement(): Element = domElement


}
