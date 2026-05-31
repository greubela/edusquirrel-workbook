package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory

case class TurtleStitchExpectedShapeCard(imageElement: HtmlImageElement) extends HtmlAppElement {

  private val headline: Element = h3(
    text <-- HtmlRenderFactory.contentIdStringSignal(LanguageMapContentId("TurtleStitch/showExpected"))
  )

  private val imgElementSignal: Signal[List[Element]] = imageElement.getDomSignal.map(preview => List(headline, preview))

  private val domElement: Element = div(
    cls := "preview-card",
    children <-- imgElementSignal,
  )

  override def getDomElement(): Element = domElement

}
