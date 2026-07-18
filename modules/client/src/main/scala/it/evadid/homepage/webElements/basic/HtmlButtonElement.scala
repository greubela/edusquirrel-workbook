package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.*
import it.evadid.homepage.webElements.basic.HtmlButtonElement.ButtonConfig
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import org.scalajs.dom.{MouseEvent, SVGSVGElement}


case class HtmlButtonElement(childElem: Signal[Element], buttonStyle: String, handleOnAction: MouseEvent => Any, config: Signal[ButtonConfig]) extends HtmlAppElement {

  private val domElement: Element = {
    button(
      cls <-- config.map(_.customStyles.mkString("", " ", s" ${buttonStyle}")),
      typ := "button",
      visibility <-- config.map(curConf => if (curConf.isVisible) "visible" else "hidden"),
      onClick --> { event => handleOnAction(event) },
      child <-- childElem
    )
  }

  override def getDomElement(): Element = domElement

}

object HtmlButtonElement {

  case class ButtonConfig(isVisible: Boolean, customStyles: List[String])

  val stdConfig: ButtonConfig = ButtonConfig(isVisible = true, customStyles = List.empty)

  def withSvgContent(svg: ReactiveSvgElement[SVGSVGElement], handleOnAction: MouseEvent => Any = _ => {}, config: ButtonConfig = stdConfig): HtmlButtonElement = {
    withSvgContent(svg, handleOnAction, Signal.fromValue(config))
  }

  def withSvgContent(svg: ReactiveSvgElement[SVGSVGElement], handleOnAction: MouseEvent => Any, config: Signal[ButtonConfig]): HtmlButtonElement = {
    HtmlButtonElement(Var(svg).signal, "button-svg", handleOnAction, config)
  }


  def withTextLabel(contentId: LanguageMapContentId, handleOnAction: MouseEvent => Any, config: Signal[ButtonConfig]): HtmlButtonElement = {
    val labelSignal: Signal[String] = LaminarRenderHelper.singleton.plaintextStringSignal(contentId)
    val elementSignal: Signal[Element] = labelSignal.map(span(_))
    HtmlButtonElement(elementSignal, "button-labeled", handleOnAction, config)
  }

  def withTextLabel(contentId: LanguageMapContentId, handleOnAction: MouseEvent => Any, config: ButtonConfig): HtmlButtonElement = {
    withTextLabel(contentId, handleOnAction, Signal.fromValue(config))
  }

  def withTextLabel(contentId: String, onAction: MouseEvent => Any = _ => {}, config: ButtonConfig = stdConfig): HtmlButtonElement = {
    withTextLabel(LanguageMapContentId(contentId), onAction, Signal.fromValue(config))
  }

  def withTextLabel(contentId: String, onAction: MouseEvent => Any, config: Signal[ButtonConfig]): HtmlButtonElement = {
    withTextLabel(LanguageMapContentId(contentId), onAction, config)
  }


}
