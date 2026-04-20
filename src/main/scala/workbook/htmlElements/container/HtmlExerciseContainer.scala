package workbook.htmlElements.container

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Signal
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo

case class HtmlExerciseContainer(
                                  fullInfo: FullInfo,
                                  children: List[HtmlWorkbookElement],
                                  level: Int = 1
                                ) extends HtmlWorkbookElement {

  private val normalizedLevel: Int = math.max(1, math.min(6, level))

  private val domElement: L.Element = L.div(
    L.cls := "container-exercise style-vbox",
    L.cls := s"container-level-$normalizedLevel",
    //L.cls := "workbook-element container-sub style-vbox",
    L.children <-- L.Var(children.map(_.getDomElement())).signal
  )

  override def getDomElement(): L.Element = domElement
}
