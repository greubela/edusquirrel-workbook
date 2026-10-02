package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.model.AllWorkbookInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.elements.structureElements.WorkbookSection


private case class SectionSelectionLine(workbook: AllWorkbookInfo) extends HtmlAppElement {

  private def sections: List[WorkbookSection] = workbook.loadedWorkbook.sections

  private def selectSection(section: WorkbookSection): Unit = {
    fullInfo.usageControl.updateWorkbookConfig(_.copy(activeSection = Some(section)))
  }

  private def isSectionActiveSignal(section: WorkbookSection): Signal[Boolean] = {
    fullInfo.signals.workbook.map(allWorkbookInfo => {
      allWorkbookInfo.exists(curInfo => curInfo.config.activeSection.contains(section))
    })
  }

  private def sectionToElement(nr: Int, section: WorkbookSection): Element = {
    div(
      cls <-- isSectionActiveSignal(section).map(isSectionShowing => if (isSectionShowing) {
        "section-block active"
      } else {
        "section-block"
      }),
      div(
        cls := "section-block-part section-block-number",
        text <-- Signal.fromValue(nr + "")
      ),
      div(
        cls := "section-block-part section-block-description",
        text <-- fullInfo.signals.stringFromLanguageMapId(section.metadata.sectionTitle)
      ),
      onClick --> { event => selectSection(section) },
    )
  }

  def sectionsToDom(sectionsWithIndex: Seq[(WorkbookSection, Int)]): Seq[Element] = {
    if (sectionsWithIndex.length <= 1) List(div())
    else sectionsWithIndex.map(cur => sectionToElement(cur._2, cur._1))
  }

  override def getDomElement(): L.Element = div(
    cls := "section-overview",
    children <-- Var(sectionsToDom(sections.zipWithIndex)).signal
  )
}
