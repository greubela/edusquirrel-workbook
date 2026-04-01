package workbook.htmlElements.headerElements

import com.raquo.laminar.api.L
import workbook.model.WorkbookSection
import workbook.model.info.AllWorkbookInfo
import com.raquo.laminar.api.L.*
import workbook.model.abstractions.HtmlWorkbookElement

import scala.concurrent.ExecutionContext

case class SectionSelectionLine(workbookInfo: AllWorkbookInfo, sections: List[WorkbookSection]) extends HtmlWorkbookElement {


  private def selectSection(section: WorkbookSection): Unit = {
    workbookInfoVar.update(curInfo => curInfo.copy(config = curInfo.config.copy(activeSection = Some(section))))
  }

  private def isSectionActiveSignal(section: WorkbookSection): Signal[Boolean] = {
    workbookInfoVar.signal.map(_.config.activeSection.contains(section))
  }

  private def sectionToElement(section: WorkbookSection): Element = {
    val titleSignal: Signal[String] = section.sectionTitleLanguageMapId match {
      case Some(mapId) => workbookInfo.stringSignalFromLanguageMapId(mapId)(ExecutionContext.global)
      case None => workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(section.sectionTitle.getInLanguage)
    }

    div(
      cls <-- isSectionActiveSignal(section).map(isSectionShowing => if (isSectionShowing) {
        "section-block active"
      } else {
        "section-block"
      }),
      div(
        text <-- titleSignal
      ) ,
      onClick --> { event => selectSection(section)},
    )
  }

  override def getDomElement(): L.Element = div(
    cls := "section-overview",
    sections.map(sectionToElement)
  )
}
