package workbook.htmlElements.headerElements

import com.raquo.laminar.api.L.*
import datastructures.core.language.{HumanLanguage, LanguageMap}
import workbook.model.WorkbookSection
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

import scala.concurrent.ExecutionContext

case class HtmlWorkbookHeader(
                               workbookInfo: AllWorkbookInfo,
                               workbookTitle: LanguageMap[HumanLanguage],
                               sections: List[WorkbookSection],
                               workbookTitleMapId: Option[String] = None
                             ) extends HtmlWorkbookElement {

  private val languageLine: LanguageSelectionLine = LanguageSelectionLine(workbookInfo)
  private val sectionLine: SectionSelectionLine = SectionSelectionLine(workbookInfo, sections)

  private val titleSignal: Signal[String] = workbookTitleMapId match {
    case Some(mapId) => workbookInfo.stringSignalFromLanguageMapId(mapId)(ExecutionContext.global)
    case None => workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(workbookTitle.getInLanguage)
  }

  private val domElement: Element = div(
    cls := "workbook-title-line",
    h1(
      text <-- titleSignal,
    ),
    languageLine.getDomElement(),
    sectionLine.getDomElement()
  )

  override def getDomElement(): Element = domElement


}
