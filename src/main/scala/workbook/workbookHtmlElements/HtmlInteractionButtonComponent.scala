package workbook.workbookHtmlElements

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import org.scalajs.dom.{MouseEvent, SVGLinearGradientElement}
import workbook.model.display.InteractionComponent
import workbook.model.display.InteractionComponent.*
import workbook.model.display.InteractionComponent.InteractionContentRole.*

abstract class HtmlInteractionButtonComponent extends InteractionComponentWithReactiveVars with InteractionComponentForRole {
  def onAction: MouseEvent => Any

  private lazy val domElement: Element = {
    div(
      cls <-- isHighlightedVar.signal.map(if (_) "svg-button highlighted" else "svg-button"),
      hidden <-- isHiddenVar.signal,
      buttonSvg
    )
  }

  def buttonSvg: Element

  override def getDomElement(): Element = domElement
}


object HtmlInteractionButtonComponent {

  private def createGradingGradient(id: String): ReactiveSvgElement[SVGLinearGradientElement] =
    svg.linearGradient(
      svg.idAttr := id,
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


  case class ShowScaffoldingButton(onAction: MouseEvent => Any) extends HtmlInteractionButtonComponent {

    val forContentRole: InteractionContentRole = ButtonShowScaffolder

    val buttonSvg: Element =
      svg.svg(
        svg.cls := "button-show-scaffolder",
        onClick --> { event => if (!this.isDisabledVar.now()) onAction(event) },
        onMouseEnter --> { event => if (!this.isDisabledVar.now()) this.setHighlight(true) },
        onMouseLeave --> { event => this.setHighlight(false) },
        svg.viewBox := "0 0 24 24",
        svg.path(
          svg.cls := "button-fill",
          svg.d := "M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z"
          //svg.d := "M17 9A5 5 0 0 0 7 9a1 1 0 0 0 2 0 3 3 0 1 1 3 3 1 1 0 0 0-1 1v2a1 1 0 0 0 2 0v-1.1A5 5 0 0 0 17 9z"
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


  case class ShowEditorButton(onAction: MouseEvent => Any) extends HtmlInteractionButtonComponent {

    val forContentRole: InteractionContentRole = ButtonShowEditor

    val buttonSvg: Element = svg.svg(
      svg.cls := "button-show-editor",
      onClick --> { event => if (!this.isDisabledVar.now()) onAction(event) },
      onMouseEnter --> { event => if (!this.isDisabledVar.now()) this.setHighlight(true) },
      onMouseLeave --> { event => this.setHighlight(false) },
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

  case class ShowGradingButton(onAction: MouseEvent => Any) extends HtmlInteractionButtonComponent {

    val forContentRole: InteractionContentRole = ButtonShowGrader

    private val gradientId = "gradient-fill-show"
    private val gradingGradient: ReactiveSvgElement[SVGLinearGradientElement] = createGradingGradient(gradientId)


    val buttonSvg: Element =
      svg.svg(
        gradingGradient,

        svg.cls := "button-grading button-grading-show",
        onClick --> { event => if (!this.isDisabledVar.now()) onAction(event) },
        onMouseEnter --> { event => if (!this.isDisabledVar.now()) this.setHighlight(true) },
        onMouseLeave --> { event => this.setHighlight(false) },
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
      )

  }

  case class StartGradingButton(onAction: MouseEvent => Any) extends HtmlInteractionButtonComponent {

    val forContentRole: InteractionContentRole = ButtonStartGrading

    private val gradientId = "gradient-fill-start"
    private val gradingGradient: ReactiveSvgElement[SVGLinearGradientElement] = createGradingGradient(gradientId)


    val buttonSvg: Element =
      svg.svg(
        gradingGradient,

        svg.cls := "button-grading button-grading-start",
        onClick --> { event => if (!this.isDisabledVar.now()) onAction(event) },
        onMouseEnter --> { event => if (!this.isDisabledVar.now()) this.setHighlight(true) },
        onMouseLeave --> { event => this.setHighlight(false) },
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
      )

  }

  case class StartScaffoldingButton(onAction: MouseEvent => Any) extends HtmlInteractionButtonComponent {

    val forContentRole: InteractionContentRole = ButtonStartScaffolding

    val buttonSvg: Element = svg.svg(
      svg.cls := "button-start-grading",
      onClick --> { event => if (!this.isDisabledVar.now()) onAction(event) },
      onMouseEnter --> { event => if (!this.isDisabledVar.now()) this.setHighlight(true) },
      onMouseLeave --> { event => this.setHighlight(false) },
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.path(
        svg.d := "M20 12L4 12",
        svg.cls := "button-stroke",
        svg.strokeWidth := "2",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      ),
      svg.path(
        svg.d := "M14 18L19.9375 12.0625V12.0625C19.972 12.028 19.972 11.972 19.9375 11.9375V11.9375L14 6",
        svg.cls := "button-stroke",
        svg.strokeWidth := "2",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      ),
    )

  }
}