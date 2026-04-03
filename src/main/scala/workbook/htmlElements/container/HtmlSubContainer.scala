package workbook.htmlElements.container

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Signal
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

case class HtmlSubContainer(
                             workbookInfo: AllWorkbookInfo,
                             children: Signal[List[HtmlWorkbookElement]],
                             level: Int = 2
                           ) extends HtmlWorkbookElement {

  private val normalizedLevel: Int = math.max(1, math.min(6, level))

  override def getDomElement(): L.Element = L.div(
    L.cls := "workbook-element container-sub style-vbox",
    L.cls := s"container-level-$normalizedLevel",
    L.children <-- children.map(_.map(_.getDomElement()))
  )
}

object HtmlSubContainer {

  def apply(workbookInfo: AllWorkbookInfo, children: List[HtmlWorkbookElement]): HtmlSubContainer =
    HtmlSubContainer(workbookInfo, L.Var(children).signal, 2)

  def withLevel(workbookInfo: AllWorkbookInfo, children: List[HtmlWorkbookElement], level: Int): HtmlSubContainer =
    HtmlSubContainer(workbookInfo, L.Var(children).signal, level)
}
