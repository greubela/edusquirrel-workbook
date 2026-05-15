package workbook.model

import com.raquo.laminar.api.L
import workbook.htmlElements.container.HtmlExerciseContainer
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo

case class WorkbookSection(
                            fullInfo: FullInfo,
                            sectionTitleLanguageMapId: String,
                            sectionContent: List[HtmlExerciseContainer],
                            sectionsRequiredBefore: List[WorkbookSection] = List(),
                            sectionsRecommendedBefore: List[WorkbookSection] = List(),
                          ) extends HtmlWorkbookElement {
  
  override lazy val workbookChildren: List[HtmlWorkbookElement] = sectionContent

  def signal: L.Signal[List[L.Element]] = L.Var(sectionContent).signal.map(_.map(_.getDomElement()))

  override def getDomElement(): L.Element = L.div(
    L.children <-- signal
  )
}
