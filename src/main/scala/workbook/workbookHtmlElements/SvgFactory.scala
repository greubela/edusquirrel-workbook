package workbook.workbookHtmlElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import org.scalajs.dom.{MouseEvent, SVGLinearGradientElement}

object SvgFactory {


  def createButtonArrowDown(onAction: MouseEvent => Unit, onEnter: MouseEvent => Unit, onLeave: MouseEvent => Unit): Element = {
    svg.svg(
      svg.cls := "svg-button button-arrow-down",
      onClick --> { event => onAction.apply(event) },
      onMouseEnter --> { event => onEnter(event) },
      onMouseLeave --> { event => onLeave(event) },
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.path(
        svg.d := "M12 20L12 4",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      ),
      svg.path(
        svg.d := "M6 14L11.9375 19.9375V19.9375C11.972 19.972 12.028 19.972 12.0625 19.9375V19.9375L18 14",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
    )
  }

  def createButtonArrowRight(onAction: MouseEvent => Unit, onEnter: MouseEvent => Unit, onLeave: MouseEvent => Unit): Element = {
    svg.svg(
      svg.cls := "svg-button button-arrow-right",
      onClick --> { event => onAction.apply(event) },
      onMouseEnter --> { event => onEnter(event) },
      onMouseLeave --> { event => onLeave(event) },
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.path(
        svg.d := "M20 12L4 12",
        svg.stroke := "#323232",
        svg.strokeWidth := "2",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      ),
      svg.path(
        svg.d := "M14 18L19.9375 12.0625V12.0625C19.972 12.028 19.972 11.972 19.9375 11.9375V11.9375L14 6",
        svg.stroke := "#323232",
        svg.strokeWidth := "2",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
    )
  }




}
