package workbook.workbookHtmlElements.basic

import com.raquo.laminar.api.L
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class HtmlSubExerciseSpacer(workbookInfoVar: L.Var[WorkbookInfo]) extends HtmlWorkbookElement {

  override def getDomElement(): L.Element = L.div(L.cls := "workbook-element exercise-spacer")
}
