package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement


object RenderingLine {
  def apply(isInteraction: Boolean, elements: Seq[Element], additionalCssStr: String): RenderingLine =
    RenderingLine(isInteraction, Signal.fromValue(elements.toList), additionalCssStr)

  def apply(isInteraction: Boolean, element: Element, additionalCssStr: String = ""): RenderingLine =
    RenderingLine(isInteraction, Signal.fromValue(List(element)), additionalCssStr)

 // def apply(isInteraction: Boolean, element: HtmlAppElement, additionalCssStr: String = ""): RenderingLine = RenderingLine(isInteraction, element.getDomElement(), additionalCssStr)
}

case class RenderingLine(isInteraction: Boolean, contentSignal: Signal[List[Element]], additionalCssStr: String) extends AtomarLineRendering {
  override lazy val render: Element = {
    div(
      cls := lineCssStr + " " + additionalCssStr,
      children <-- contentSignal
    )
  }
  override lazy val elementsWithoutContainer: Signal[List[Element]] = contentSignal
}
