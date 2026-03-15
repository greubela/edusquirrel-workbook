package workbook.workbookHtmlElements.head

import com.raquo.laminar.api.L
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import workbook.model.WorkbookSection
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import com.raquo.laminar.api.L.*

case class SectionSelectionLine(workbookInfoVar: Var[WorkbookInfo], sections: List[WorkbookSection]) extends HtmlWorkbookElement {


  private def selectSection(section: WorkbookSection): Unit = {
    workbookInfoVar.update(curInfo => curInfo.copy(config = curInfo.config.copy(activeSection = Some(section))))
  }
  /*
  <div class="progress-steps"><div class="progress-step active">0. Motivation</div><div class="progress-step">1. Bauteile &amp; Aufbau</div><div class="progress-step">2. Pumpe steuern</div><div class="progress-step">3. Feuchtigkeit messen</div><div class="progress-step">4. Messwerte &amp; Pumpe</div><div class="progress-step">5. Test &amp; Fertig</div></div>
   */

  private def isSectionActiveSignal(section: WorkbookSection): Signal[Boolean] = {
    workbookInfoVar.signal.map(_.config.activeSection.contains(section))
  }

  private def sectionToElement(section: WorkbookSection): Element = {
    div(
      cls <-- isSectionActiveSignal(section).map(isSectionShowing => if (isSectionShowing) {
        "section-block active"
      } else {
        "section-block"
      }),
      child <-- workbookInfoVar.signal.map(_.languageStringFromMap(section.sectionTitle)),
      onClick --> { event => selectSection(section)},
    )
  }

  override def getDomElement(): L.Element = div(
    cls := "section-overview",
    sections.map(sectionToElement)
  )
}
