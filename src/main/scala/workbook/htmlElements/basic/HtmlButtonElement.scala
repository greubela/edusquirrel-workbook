package workbook.htmlElements.basic

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import contentmanagement.webElements.HtmlAppElement
import org.scalajs.dom.{MouseEvent, SVGSVGElement}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.WorkbookInfo

case class HtmlButtonElement(workbookInfoVar: Var[WorkbookInfo], childElem: Element, onAction: MouseEvent => Any) extends HtmlWorkbookElement {

  private val domElement: Element = {

    div(
      cls := "svg-button",
      onClick --> { event => onAction(event)},
      /*cls <-- isHighlightedVar.signal.map(if (_) "svg-button highlighted" else "svg-button"),
      hidden <-- isHiddenVar.signal,

      onClick --> { event => if (!this.isDisabledVar.now()) onAction(event) },
      onMouseEnter --> { event => if (!this.isDisabledVar.now()) this.setHighlight(true) },
      onMouseLeave --> { event => this.setHighlight(false) },*/
      childElem
    )
  }


  override def getDomElement(): Element = domElement

}
object HtmlButtonElement {

  def apply(workbookInfoVar: Var[WorkbookInfo], buttonSvg: ReactiveSvgElement[SVGSVGElement], onAction: MouseEvent => Any): HtmlButtonElement = {
    val refElement: Element = div(buttonSvg)
    HtmlButtonElement(workbookInfoVar, refElement, onAction)
  }

  def apply(workbookInfoVar: Var[WorkbookInfo], string: String, onAction: MouseEvent => Any): HtmlButtonElement = {
    println("[WARN] String button should not be created, just for demo!!: " + string)
    HtmlButtonElement(
      workbookInfoVar,
      div(
        string
      ),
      onAction
    )
  }
}
