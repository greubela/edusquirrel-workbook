package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.*
import it.evadid.homepage.workbook.htmlRenderer.{HtmlRenderFactory, LaminarRenderHelper}
import org.scalajs.dom.{MouseEvent, SVGSVGElement}

case class HtmlButtonElement(childElem: Signal[Element], additionalStyles: String, handleOnAction: MouseEvent => Any) extends HtmlAppElement {

  private val domElement: Element = {
    button(
      cls := additionalStyles,
      typ := "button",
      onClick --> { event => handleOnAction(event) },
      child <-- childElem
    )
  }

  override def getDomElement(): Element = domElement

}

object HtmlButtonElement {

  def withSvgContent(svg: ReactiveSvgElement[SVGSVGElement], onAction: MouseEvent => Any): HtmlButtonElement = {
    HtmlButtonElement(Var(svg).signal, "button-svg", onAction)
    // todo: svg dimensions
  }

  def withTextLabel(contentId: LanguageMapContentId, onAction: MouseEvent => Any): HtmlButtonElement = {
    val labelSignal: Signal[String] = LaminarRenderHelper.singleton.plaintextStringSignal(contentId)
    val elementSignal: Signal[Element] = labelSignal.map(span(_))
    HtmlButtonElement(elementSignal, "button-labeled", onAction)
  }

  def withTextLabel(contentId: String, onAction: MouseEvent => Any): HtmlButtonElement = {
    withTextLabel(LanguageMapContentId(contentId), onAction)
  }



}
