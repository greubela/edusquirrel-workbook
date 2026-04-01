package interactionPlugins.turtleStitchPlugin.card

import com.raquo.laminar.api.L.*
import datastructures.web.file.FullImage
import workbook.htmlElements.basic.HtmlImageElement
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

import scala.concurrent.ExecutionContext

case class TurtleStitchExpectedShapeCard(workbookInfo: AllWorkbookInfo, expectedOutcome: FullImage) extends HtmlWorkbookElement {

  private val headline: Element = h3(
    text <-- workbookInfo.stringSignalFromLanguageMapId("TurtleStitch/showExpected")(ExecutionContext.global)
  )

  private val imgElementSignal: Signal[Element] = {
    HtmlImageElement(expectedOutcome, workbookInfo).getDomSignal
  }


  private val domElement: Element = div(
    cls := "preview-card",
    children <-- imgElementSignal.map(preview => List(headline, preview)),
  )

  override def getDomElement(): Element = domElement

}
