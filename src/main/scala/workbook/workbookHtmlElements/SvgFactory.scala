package workbook.workbookHtmlElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import org.scalajs.dom.{MouseEvent, SVGLinearGradientElement}

object SvgFactory {

  def createButtonScaffolding(onAction: MouseEvent => Unit, onEnter: MouseEvent => Unit, onLeave: MouseEvent => Unit): Element = {
    svg.svg(
      svg.cls := "svg-button button-scaffolding",
      onClick --> { event => onAction.apply(event) },
      onMouseEnter --> { event => onEnter(event) },
      onMouseLeave --> { event => onLeave(event) },
      svg.viewBox := "0 0 24 24",
      svg.path(
        svg.cls := "button-borderpath",
        svg.d := "M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z"
      ),
      svg.path(
        svg.d := "M10.5 8.67709C10.8665 8.26188 11.4027 8 12 8C13.1046 8 14 8.89543 14 10C14 10.9337 13.3601 11.718 12.4949 11.9383C12.2273 12.0064 12 12.2239 12 12.5V12.5V13",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"),
      svg.path(
        svg.d := "M12 16H12.01",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round")
    )
  }

  def createButtonEditor(onAction: MouseEvent => Unit, onEnter: MouseEvent => Unit, onLeave: MouseEvent => Unit): Element = {
    svg.svg(
      svg.cls := "svg-button button-editor",
      onClick --> { event => onAction.apply(event) },
      onMouseEnter --> { event => onEnter(event) },
      onMouseLeave --> { event => onLeave(event) },
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.path(
        svg.cls := "button-borderpath",
        svg.d := "M13 21H21",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      ),
      svg.path(
        svg.d := "M20.0651 7.39423L7.09967 20.4114C6.72438 20.7882 6.21446 21 5.68265 21H4.00383C3.44943 21 3 20.5466 3 19.9922V18.2987C3 17.7696 3.20962 17.2621 3.58297 16.8873L16.5517 3.86681C19.5632 1.34721 22.5747 4.87462 20.0651 7.39423Z",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"),
      svg.path(
        svg.d := "M15.3097 5.30981L18.7274 8.72755",
      )
    )
  }

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

  def createGradingGradient(): ReactiveSvgElement[SVGLinearGradientElement] = {
    svg.linearGradient(
      svg.idAttr := "gradient-fill",
      svg.x1 := "4",
      svg.x2 := "20",
      svg.y1 := "0",
      svg.y2 := "0",
      svg.gradientUnits := "userSpaceOnUse",
      svg.stop(
        svg.offsetAttr := "0",
        svg.stopColor := "#00ff00",
      ),
      svg.stop(
        svg.offsetAttr := "0.5",
        svg.stopColor := "#ffff00",
      ),
      svg.stop(
        svg.offsetAttr := "1",
        svg.stopColor := "#ff0000",
      )
    )
  }

  def createButtonGrading(onAction: MouseEvent => Unit, onEnter: MouseEvent => Unit, onLeave: MouseEvent => Unit): Element = {
    svg.svg(
      createGradingGradient(),

      svg.cls := "svg-button button-grading",
      onClick --> { event => onAction.apply(event) },
      onMouseEnter --> { event => onEnter(event) },
      onMouseLeave --> { event => onLeave(event) },
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.path(
        svg.cls := "button-borderpath",
        svg.d := "M3 12C3 4.5885 4.5885 3 12 3C19.4115 3 21 4.5885 21 12C21 19.4115 19.4115 21 12 21C4.5885 21 3 19.4115 3 12Z"
      ),
      svg.path(
        svg.d := "M12 8L12 16",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
      ,
      svg.path(
        svg.d := "M15 11L12.087 8.08704V8.08704C12.039 8.03897 11.961 8.03897 11.913 8.08704V8.08704L9 11",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
      ,
    )
  }


}
