package workbook.model

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.core.language.{HumanLanguage, LanguageMap}
import workbook.htmlElements.container.HtmlExerciseContainer
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

case class WorkbookSection(
                            workbookInfo: AllWorkbookInfo,
                            sectionTitle: LanguageMap[HumanLanguage],
                            sectionContent: List[HtmlExerciseContainer],
                            sectionsRequiredBefore: List[WorkbookSection] = List(),
                            sectionsRecommendedBefore: List[WorkbookSection] = List(),
                            sectionTitleLanguageMapId: Option[String] = None,
                          ) extends HtmlWorkbookElement {


  override def getDomElement(): L.Element = div(
    children <-- Var(sectionContent).signal.map(_.map(_.getDomElement()))
  )
}

object WorkbookSection {
  def apply(workbookInfo: AllWorkbookInfo, sectionTitleLanguageMapId: String, sectionContent: List[HtmlExerciseContainer]): WorkbookSection = {
    WorkbookSection(workbookInfo, LanguageMap.mapBasedLanguageMap(Map.empty[HumanLanguage, String]), sectionContent, sectionTitleLanguageMapId = Some(sectionTitleLanguageMapId))
  }
}
