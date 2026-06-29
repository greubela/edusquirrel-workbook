package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.model.elements.*


private case class SectionSelectionLine(workbook: Workbook) extends HtmlAppElement {

  private def sections: List[WorkbookSection] = workbook.sections

  private def selectSection(section: WorkbookSection): Unit = {
    fullInfo.control.updateWorkbookConfig(_.copy(activeSection = Some(section)))
  }

  private def isSectionActiveSignal(section: WorkbookSection): Signal[Boolean] = {
    fullInfo.signals.workbook.map(allWorkbookInfo => {
      allWorkbookInfo.exists(curInfo => curInfo.config.activeSection.contains(section))
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
        text <-- fullInfo.signals.stringFromLanguageMapId(section.sectionTitle)
      ),
      onClick --> { event => selectSection(section) },
    )
  }

  override def getDomElement(): L.Element = div(
    cls := "section-overview",
    children <-- Var(sections.map(sectionToElement)).signal
  )
}
