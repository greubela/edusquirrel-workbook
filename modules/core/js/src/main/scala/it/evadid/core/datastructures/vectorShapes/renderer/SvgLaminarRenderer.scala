package it.evadid.core.datastructures.vectorShapes.renderer


import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.StringAsIsCodec
import com.raquo.laminar.keys.SvgAttr
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.color.AppColor
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.{AppElementRendered, AppShapeAtomar, AppShapeComposition}
import it.evadid.core.datastructures.vectorShapes.atomar.{AppShapeDrawingRoutineElement, AppShapeTextElement, TextPlacement}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.util.logging.Logger
import org.scalajs.dom.SVGSVGElement

object SvgLaminarRenderer extends SvgRenderer[Double, ReactiveSvgElement[SVGSVGElement]] {
  private val fontStyleAttr = SvgAttr("font-style", StringAsIsCodec, None)

  /** Extra modifiers for one laid-out element, e.g. event handlers derived from its configuration. */
  type Decoration = AppElementRendered[Double] => Seq[Modifier[SvgElement]]

  private val noDecoration: Decoration = _ => Nil

  /** Opaque colours keep their hex form; translucent ones need the alpha channel. */
  private def paint(color: AppColor): String = {
    val rgb = color.toRGB
    if rgb.alpha < 255 then s"rgba(${rgb.red}, ${rgb.green}, ${rgb.blue}, ${rgb.alpha / 255.0})"
    else color.toWebColor.webStyleHexString
  }

  private def fill(config: AppShapeElementConfig[Double]): String =
    if config.fillEnabled then paint(config.colorFill) else "none"

  private def strokeStyle(config: AppShapeElementConfig[Double]): Seq[Modifier[SvgElement]] = Seq(
    svg.stroke := paint(config.colorStroke),
    svg.strokeWidth := config.strokeWidth.toString,
    svg.strokeMiterLimit := "4"
  ) ++ config.strokeDashArray.map(svg.strokeDashArray := _)

  def renderSvgImage(logger: Logger, shape: AppElementRendered[Double], decorate: Decoration = noDecoration): ReactiveSvgElement[SVGSVGElement] = {
    val viewport = SvgViewport.boundsFor(shape)
    svg.svg(
      svg.width := s"${viewport.width}",
      svg.height := s"${viewport.height}",
      svg.viewBox := s"${viewport.startX} ${viewport.startY} ${viewport.width} ${viewport.height}",
      svg.fill := fill(shape.elementConfig),
      svg.stroke := paint(shape.elementConfig.colorStroke),
      svg.strokeWidth := shape.elementConfig.strokeWidth.toString,
      onClick --> { event => shape.elementConfig.onMouseClicked(event.button == 0) },
      renderElementAsSvg(logger, shape, decorate)
    )
  }

  def renderElementAsSvg(logger: Logger, shape: AppElementRendered[Double], decorate: Decoration = noDecoration): ReactiveSvgElement[?] = {
    val config = shape.elementConfig
    val clicked: Modifier[SvgElement] = onClick --> { event =>
      event.stopPropagation()
      config.onMouseClicked(event.button == 0)
    }
    shape.baseElement match {
      case a: AppShapeAtomar[Double] => a match {
        case dr: AppShapeDrawingRoutineElement[Double] =>
          svg.path(
            svg.x := "0",
            svg.y := "0",
            svg.fill := fill(config),
            strokeStyle(config),
            clicked,
            svg.d := dr.renderPath(logger, shape.myBounds).svgPathDString,
            decorate(shape)
          )
        case AppShapeTextElement(text, _, placement) =>
          val position = placement match {
            case TextPlacement.FitToBounds =>
              val measured = shape.compositionPositioned.compositionDimensioned.compositionMeasurd.minimumDimension.rawDimension
              val scaleX = if measured.width > 0 then shape.myBounds.width / measured.width else 1.0
              val scaleY = if measured.height > 0 then shape.myBounds.height / measured.height else 1.0
              Seq(
                svg.x := "" + shape.myBounds.startPoint.x,
                svg.y := "" + shape.myBounds.startPoint.y,
                svg.fontSize := s"${config.font.sizeInPx * math.min(scaleX, scaleY)}px",
                svg.dominantBaseline := "hanging")
            case TextPlacement.Centered =>
              // Padding is reserved after the text; centre on the whole allocated box.
              val center = shape.outerBounds.centerPoint
              Seq(
                svg.x := center.x.toString,
                svg.y := center.y.toString,
                svg.fontSize := s"${config.font.sizeInPx}px",
                svg.textAnchor := "middle",
                svg.dominantBaseline := "middle")
          }
          svg.text(
            position,
            svg.fill := paint(config.colorFont),
            strokeStyle(config),
            svg.fontFamily := config.font.name,
            svg.fontWeight := (if config.font.bold then "700" else "400"),
            fontStyleAttr := (if config.font.italic then "italic" else "normal"),
            svg.fontVariant := config.font.variant,
            clicked,
            text,
            decorate(shape)
          )
        case other => throw IllegalArgumentException(s"No SVG rendering for ${other.getClass.getSimpleName}.")
      }
      case _: AppShapeComposition[Double] =>
        svg.g(
          clicked,
          shape.children.map(renderElementAsSvg(logger, _, decorate)),
          decorate(shape)
        )
    }
  }

  override def render(logger: Logger, input: AppShapeElement[Double]): ReactiveSvgElement[SVGSVGElement] = {
    val rendered: AppShapeElement.AppElementRendered[Double] = input.renderWithMinimumDimension(AppShapeRenderingConfig.defaultDouble)
    renderSvgImage(logger, rendered)
  }
}
