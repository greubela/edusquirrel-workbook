package workbook.htmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import contentmanagement.webElements.HtmlAppElement
import org.scalajs.dom.{MouseEvent, SVGSVGElement}
import workbook.model.abstractions.HtmlWorkbookElement

import scala.concurrent.ExecutionContext

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.info.{FullInfo, HomepageInfo}

case class HtmlButtonElement(fullInfo: FullInfo, childElem: Signal[Element], handleOnAction: MouseEvent => Any) extends HtmlWorkbookElement {

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

  def withTextLabel(fullInfo: FullInfo, languageMapId: String, onAction: MouseEvent => Any): HtmlButtonElement = {
    val labelSignal: Signal[String] = fullInfo.signals.stringFromLanguageMapId(languageMapId)
    val elementSignal: Signal[Element] = labelSignal.map(span(_))
    HtmlButtonElement(fullInfo, elementSignal, onAction)
  }

  def withSvgContent(homepageInfo: FullInfo, svg: ReactiveSvgElement[SVGSVGElement], onAction: MouseEvent => Any): HtmlButtonElement = {
    val dom = div(cls := "svg-button", svg)
    HtmlButtonElement(homepageInfo, Var(dom).signal, onAction)
    // todo: svg dimensions
  }


}
