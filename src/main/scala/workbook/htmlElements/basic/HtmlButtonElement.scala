package workbook.htmlElements.basic

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import contentmanagement.webElements.HtmlAppElement
import org.scalajs.dom.{MouseEvent, SVGSVGElement}
import workbook.model.abstractions.HtmlWorkbookElement

case class HtmlButtonElement(val buttonSvg: ReactiveSvgElement[SVGSVGElement], onAction: MouseEvent => Any) extends HtmlAppElement {

  private val domElement: Element = {

    div(
      cls := "svg-button",
      onClick --> { event => onAction(event)},
      /*cls <-- isHighlightedVar.signal.map(if (_) "svg-button highlighted" else "svg-button"),
      hidden <-- isHiddenVar.signal,

      onClick --> { event => if (!this.isDisabledVar.now()) onAction(event) },
      onMouseEnter --> { event => if (!this.isDisabledVar.now()) this.setHighlight(true) },
      onMouseLeave --> { event => this.setHighlight(false) },*/
      buttonSvg
    )
  }


  override def getDomElement(): Element = domElement

}
