package workbook.workbookHtmlElements.basic

import com.raquo.laminar.api.L.*
import org.scalajs.dom.MouseEvent
import workbook.workbookHtmlElements.abstractions.{HtmlWorkbookElement, InteractionComponentTemplate}

case class HtmlButtonElement(buttonSvg: Element, onAction: MouseEvent => Any) extends InteractionComponentTemplate with HtmlWorkbookElement {
  
  private lazy val domElement: Element = {
    div(
      cls <-- isHighlightedVar.signal.map(if (_) "svg-button highlighted" else "svg-button"),
      hidden <-- isHiddenVar.signal,

      onClick --> { event => if (!this.isDisabledVar.now()) onAction(event) },
      onMouseEnter --> { event => if (!this.isDisabledVar.now()) this.setHighlight(true) },
      onMouseLeave --> { event => this.setHighlight(false) },

      buttonSvg
    )
  }


  override def getDomElement(): Element = domElement

}
