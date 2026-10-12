package it.evadid.core.datastructures.vectorShapes.renderer


import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.StringAsIsCodec
import com.raquo.laminar.keys.SvgAttr
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.{AppElementRendered, AppShapeAtomar, AppShapeComposition}
import it.evadid.core.datastructures.vectorShapes.atomar.{AppShapeDrawingRoutineElement, AppShapeTextElement}
import it.evadid.core.datastructures.vectorShapes.config.AppShapeRenderingConfig
import it.evadid.util.logging.Logger
import org.scalajs.dom.SVGSVGElement

object SvgLaminarRenderer extends SvgRenderer[Double, ReactiveSvgElement[SVGSVGElement]] {
  private val fontStyleAttr = SvgAttr("font-style", StringAsIsCodec, None)

  def renderSvgImage(logger: Logger, shape: AppElementRendered[Double]): ReactiveSvgElement[SVGSVGElement] = {
    val viewport = SvgViewport.boundsFor(shape)
    svg.svg(
      svg.width := s"${viewport.width}",
      svg.height := s"${viewport.height}",
      svg.viewBox := s"${viewport.startX} ${viewport.startY} ${viewport.width} ${viewport.height}",
      svg.fill := (if shape.elementConfig.fillEnabled then shape.elementConfig.colorFill.toWebColor.webStyleHexString else "none"),
      svg.stroke := shape.elementConfig.colorStroke.toWebColor.webStyleHexString,
      svg.strokeWidth := shape.elementConfig.strokeWidth.toString,
      onClick --> { event => shape.elementConfig.onMouseClicked(event.button == 0) },
      renderElementAsSvg(logger, shape)
    )
  }

  def renderElementAsSvg(logger: Logger, shape: AppElementRendered[Double]): ReactiveSvgElement[?] = {

    shape.baseElement.match {
      case a: AppShapeAtomar[Double] => {
        a.match {
          case dr@AppShapeDrawingRoutineElement(routine, elementConfig, minSize) => {
            svg.path(
              svg.x := "0",
              svg.y := "0",
              svg.fill := (if elementConfig.fillEnabled then elementConfig.colorFill.toWebColor.webStyleHexString else "none"),
              svg.stroke := elementConfig.colorStroke.toWebColor.webStyleHexString,
              svg.strokeWidth := elementConfig.strokeWidth.toString,
              svg.strokeMiterLimit := "4",
              onClick --> { event =>
                event.stopPropagation()
                elementConfig.onMouseClicked(event.button == 0)
              },
              svg.d := dr.renderPath(logger, shape.myBounds).svgPathDString
            )
          }
          case AppShapeTextElement(text, elementConfig) => {
            val measured = shape.compositionPositioned.compositionDimensioned.compositionMeasurd.minimumDimension.rawDimension
            val scaleX = if measured.width > 0 then shape.myBounds.width / measured.width else 1.0
            val scaleY = if measured.height > 0 then shape.myBounds.height / measured.height else 1.0
            val scale = math.min(scaleX, scaleY)
            svg.text(
              svg.x := "" + shape.myBounds.startPoint.x,
              svg.y := "" + shape.myBounds.startPoint.y,
              svg.fill := elementConfig.colorFont.toWebColor.webStyleHexString,
              svg.stroke := elementConfig.colorStroke.toWebColor.webStyleHexString,
              svg.strokeWidth := elementConfig.strokeWidth.toString,
              svg.strokeMiterLimit := "4",
              svg.fontSize := (elementConfig.font.sizeInPx * scale) + "px",
              svg.fontFamily := elementConfig.font.name,
              svg.fontWeight := (if elementConfig.font.bold then "700" else "400"),
              fontStyleAttr := (if elementConfig.font.italic then "italic" else "normal"),
              svg.fontVariant := elementConfig.font.variant,
              onClick --> { event =>
                event.stopPropagation()
                elementConfig.onMouseClicked(event.button == 0)
              },
              svg.dominantBaseline := "hanging",
              text
            )
          }
        }
      }
      case c: AppShapeComposition[Double] => {
        svg.g(
          onClick --> { event =>
            event.stopPropagation()
            c.elementConfig.onMouseClicked(event.button == 0)
          },
          shape.children.map(renderElementAsSvg(logger, _))
        )
      }
    }
  }

  override def render(logger: Logger, input: AppShapeElement[Double]): ReactiveSvgElement[SVGSVGElement] = {
    val rendered: AppShapeElement.AppElementRendered[Double] = input.renderWithMinimumDimension(AppShapeRenderingConfig.defaultDouble)
    renderSvgImage(logger, rendered)
  }
}
