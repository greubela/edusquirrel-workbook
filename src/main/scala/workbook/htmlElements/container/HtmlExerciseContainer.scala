package workbook.htmlElements.container

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Signal
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

case class HtmlExerciseContainer(
                                  workbookInfo: AllWorkbookInfo,
                                  children: Signal[List[HtmlWorkbookElement]],
                                  level: Int = 1
                                ) extends HtmlWorkbookElement {

  private val normalizedLevel: Int = math.max(1, math.min(6, level))

  private val domElement: L.Element = L.div(
    L.cls := "container-exercise style-vbox",
    L.cls := s"container-level-$normalizedLevel",
    L.children <-- children.map(_.map(_.getDomElement()))
  )

  override def getDomElement(): L.Element = domElement
}

object HtmlExerciseContainer {

  def apply(workbookInfo: AllWorkbookInfo, children: List[HtmlWorkbookElement]): HtmlExerciseContainer =
    HtmlExerciseContainer(workbookInfo, L.Var(children).signal, 1)

  def withLevel(workbookInfo: AllWorkbookInfo, children: List[HtmlWorkbookElement], level: Int): HtmlExerciseContainer =
    HtmlExerciseContainer(workbookInfo, L.Var(children).signal, level)
}
