package workbook.htmlElements.container

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Signal
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, FullInfo}

case class HtmlExerciseContainer(
                                  fullInfo: FullInfo,
                                  children: List[HtmlWorkbookElement],
                                  level: Int = 1,
                                  isMainContainer: Boolean = true
                                ) extends HtmlWorkbookElement {

  private val normalizedLevel: Int = math.max(1, math.min(6, level))

  private val clsString = if(isMainContainer) s"container-exercise style-vbox container-level-$normalizedLevel" else s"container-sub container-level-$normalizedLevel"

  private val domElement: L.Element = L.div(
    //L.cls := "container-exercise style-vbox",
   // L.cls := s"container-level-$normalizedLevel",
    L.cls := clsString,
    L.children <-- L.Var(children.map(_.getDomElement())).signal
  )

  override def getDomElement(): L.Element = domElement

  /* stolen from sub
  override def getDomElement(): L.Element = L.div(
    L.cls := "workbook-element container-sub style-vbox",
    L.cls := s"container-level-$normalizedLevel",
    L.children <-- children.map(_.map(_.getDomElement()))
  )*/
}

object HtmlExerciseContainer {


}