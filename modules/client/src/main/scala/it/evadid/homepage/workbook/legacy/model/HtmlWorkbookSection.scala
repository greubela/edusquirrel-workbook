package it.evadid.homepage.workbook.legacy.model
/*
import com.raquo.laminar.api.L
import it.evadid.homepage.workbook.legacy.htmlElements.container.HtmlExerciseContainer
import it.evadid.homepage.workbook.legacy.model.abstractions.HtmlWorkbookElement
import it.evadid.homepage.workbook.legacy.model.info.FullInfo

case class HtmlWorkbookSection(
                                fullInfo: FullInfo,
                                sectionTitleLanguageMapId: String,
                                sectionContent: List[HtmlExerciseContainer],
                                sectionsRequiredBefore: List[HtmlWorkbookSection] = List(),
                                sectionsRecommendedBefore: List[HtmlWorkbookSection] = List(),
                          ) extends HtmlWorkbookElement {
  
 // override lazy val workbookChildren: List[HtmlWorkbookElement] = sectionContent

  def signal: L.Signal[List[L.Element]] = L.Var(sectionContent).signal.map(_.map(_.getDomElement()))

  override def getDomElement(): L.Element = L.div(
    L.children <-- signal
  )
}
*/