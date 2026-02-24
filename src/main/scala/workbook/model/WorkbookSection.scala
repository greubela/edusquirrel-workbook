package workbook.model

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.*
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.container.HtmlExerciseContainer

case class WorkbookSection(
                            workbookInfoVar: Var[WorkbookInfo],
                            sectionTitle: LanguageMap[HumanLanguage],
                            sectionContent: List[HtmlExerciseContainer],
                            sectionsRequiredBefore: List[WorkbookSection] = List(),
                            sectionsRecommendedBefore: List[WorkbookSection] = List(),
                          ) extends HtmlWorkbookElement {


  override def getDomElement(): L.Element = div(
    children <-- Var(sectionContent).signal.map(_.map(_.getDomElement()))
  )
}
