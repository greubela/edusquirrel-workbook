package workbook.htmlElements.container

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Signal
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.WorkbookInfo

case class HtmlExerciseContainer(workbookInfoVar: L.Var[WorkbookInfo], children: Signal[List[HtmlWorkbookElement]]) extends HtmlWorkbookElement{

  private val domElement: L.Element = L.div(
    L.cls := "container-exercise style-vbox",
    L.children <-- children.map(_.map(_.getDomElement()))
  )
  override def getDomElement(): L.Element = domElement
}
object HtmlExerciseContainer {
  def apply(configVar: L.Var[WorkbookInfo], children: List[HtmlWorkbookElement]): HtmlExerciseContainer = HtmlExerciseContainer(configVar, L.Var(children).signal)
}