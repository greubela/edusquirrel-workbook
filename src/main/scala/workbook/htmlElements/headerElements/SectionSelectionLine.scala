package workbook.htmlElements.headerElements

import com.raquo.laminar.api.L
import workbook.model.WorkbookSection
import com.raquo.laminar.api.L.*
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo

import scala.concurrent.ExecutionContext

case class SectionSelectionLine(fullInfo: FullInfo, sections: List[WorkbookSection]) extends HtmlWorkbookElement {


  private def selectSection(section: WorkbookSection): Unit = {
    fullInfo.control.updateWorkbookConfig(_.copy(activeSection = Some(section)))
  }

  private def isSectionActiveSignal(section: WorkbookSection): Signal[Boolean] = {
    fullInfo.signals.workbook.map(allWorkbookInfo => {
      allWorkbookInfo.map(curInfo => curInfo.config.activeSection == section).getOrElse(false)
    })
  }

  private def sectionToElement(section: WorkbookSection): Element = {
    
    div(
      cls <-- isSectionActiveSignal(section).map(isSectionShowing => if (isSectionShowing) {
        "section-block active"
      } else {
        "section-block"
      }),
      div(
        text <-- fullInfo.signals.stringFromLanguageMapId(section.sectionTitleLanguageMapId)
      ) ,
      onClick --> { event => selectSection(section)},
    )
  }

  override def getDomElement(): L.Element = div(
    cls := "section-overview",
    sections.map(sectionToElement)
  )
}
