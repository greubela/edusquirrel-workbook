package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.homepage.webElements.*
import it.evadid.homepage.workbook.legacy.model.info.FullInfo
import org.scalajs.dom.{MouseEvent, SVGSVGElement}
import it.evadid.homepage.webElements.*
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory;

case class HtmlButtonElement(childElem: Signal[Element], handleOnAction: MouseEvent => Any) extends HtmlAppElement {

  private val domElement: Element = {
    button(
      typ := "button",
      onClick --> { event => handleOnAction(event) },
      child <-- childElem
    )
  }

  override def getDomElement(): Element = domElement

}

object HtmlButtonElement {

  def withTextLabel(contentId: LanguageMapContentId, onAction: MouseEvent => Any): HtmlButtonElement = {
    val labelSignal: Signal[String] = HtmlRenderFactory.contentIdStringSignal(contentId)
    val elementSignal: Signal[Element] = labelSignal.map(span(_))
    HtmlButtonElement(elementSignal, onAction)
  }

  def withSvgContent(svg: ReactiveSvgElement[SVGSVGElement], onAction: MouseEvent => Any): HtmlButtonElement = {
    val dom = div(cls := "svg-button", svg)
    HtmlButtonElement(Var(dom).signal, onAction)
    // todo: svg dimensions
  }


}
