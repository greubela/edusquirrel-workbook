package interactionPlugins.turtleStitchPlugin.card

import com.raquo.laminar.api.L.*
import datastructures.web.file.FullImage
import workbook.htmlElements.basic.HtmlImageElement
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo

import scala.concurrent.ExecutionContext

case class TurtleStitchExpectedShapeCard(fullInfo: FullInfo, imageElement: HtmlImageElement) extends HtmlWorkbookElement {

  private val headline: Element = h3(
    text <-- fullInfo.signals.stringFromLanguageMapId("TurtleStitch/showExpected")
  )

  private val imgElementSignal: Signal[Element] = imageElement.getDomSignal

  private val domElement: Element = div(
    cls := "preview-card",
    children <-- imgElementSignal.map(preview => List(headline, preview)),
  )

  override def getDomElement(): Element = domElement

}
