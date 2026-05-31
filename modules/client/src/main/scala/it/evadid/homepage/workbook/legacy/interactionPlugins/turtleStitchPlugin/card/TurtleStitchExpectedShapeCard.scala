package it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.card

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.legacy.htmlElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.legacy.model.abstractions.HtmlWorkbookElement
import it.evadid.homepage.workbook.legacy.model.info.FullInfo

import scala.concurrent.ExecutionContext

case class TurtleStitchExpectedShapeCard(fullInfo: FullInfo, imageElement: HtmlImageElement) extends HtmlWorkbookElement {

  private val headline: Element = h3(
    text <-- fullInfo.signals.stringFromLanguageMapId("TurtleStitch/showExpected")
  )

  private val imgElementSignal: Signal[List[Element]] = imageElement.getDomSignal.map(preview => List(headline, preview))

  private val domElement: Element = div(
    cls := "preview-card",
    children <-- imgElementSignal,
  )

  override def getDomElement(): Element = domElement

}
