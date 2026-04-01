package workbook.htmlElements.container

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Signal
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}

case class HtmlExerciseContainer(workbookInfo: AllWorkbookInfo, children: Signal[List[HtmlWorkbookElement]]) extends HtmlWorkbookElement{

  private val domElement: L.Element = L.div(
    L.cls := "container-exercise style-vbox",
    L.children <-- children.map(_.map(_.getDomElement()))
  )
  override def getDomElement(): L.Element = domElement
}
object HtmlExerciseContainer {
  
  def apply(workbookInfo: AllWorkbookInfo, children: List[HtmlWorkbookElement]): HtmlExerciseContainer = 
    HtmlExerciseContainer(workbookInfo, L.Var(children).signal)
}