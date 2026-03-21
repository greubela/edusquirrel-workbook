package interactionPlugins.turtleStitchPlugin.card

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.file.*
import interactionPlugins.turtleStitchPlugin.TurtleStitchLanguageMaps
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.WorkbookInfo
import workbook.htmlElements.basic.HtmlImageElement

import scala.concurrent.ExecutionContext

case class TurtleStitchExpectedShapeCard(workbookInfoVar: L.Var[WorkbookInfo], expectedOutcome: FullImage) extends HtmlWorkbookElement {

  private val headline: Element = h3(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(TurtleStitchLanguageMaps.languageMapShowExpected))
  )

  private val imgElementSignal: Signal[Element] = {
    HtmlImageElement(expectedOutcome, workbookInfoVar).getDomSignal()
  }


  private val domElement: Element = div(
    cls := "preview-card",
    children <-- imgElementSignal.map(preview => List(headline, preview)),
  )

  override def getDomElement(): Element = domElement

}
