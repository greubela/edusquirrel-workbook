package it.evadid.core.datastructures.vectorShapes.renderer


import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.{AppElementRendered, AppShapeAtomar, AppShapeComposition}
import it.evadid.core.datastructures.vectorShapes.atomar.{AppShapeDrawingRoutineElement, AppShapeTextElement}
import it.evadid.core.datastructures.vectorShapes.config.AppShapeRenderingConfig
import it.evadid.util.logging.Logger
import org.scalajs.dom.SVGSVGElement

object SvgLaminarRenderer extends SvgRenderer[Double, ReactiveSvgElement[SVGSVGElement]] {

  def renderSvgImage(logger: Logger, shape: AppElementRendered[Double]): ReactiveSvgElement[SVGSVGElement] = {
    svg.svg(
      svg.width := s"${shape.myBounds.dimension.width}",
      svg.height := s"${shape.myBounds.dimension.height}",
      svg.viewBox := s"${shape.myBounds.startPoint.x} ${shape.myBounds.startPoint.y} ${shape.myBounds.dimension.width} ${shape.myBounds.dimension.height}",
      svg.fill := shape.elementConfig.colorFill.toWebColor.webStyleHexString,
      svg.stroke := shape.elementConfig.colorStroke.toWebColor.webStyleHexString,
      onClick --> { event => shape.elementConfig.onMouseClicked(event.button == 1) },
      renderElementAsSvg(logger, shape)
    )
  }

  def renderElementAsSvg(logger: Logger, shape: AppElementRendered[Double]): ReactiveSvgElement[?] = {

    shape.baseElement.match {
      case a: AppShapeAtomar[Double] => {
        a.match {
          case dr@AppShapeDrawingRoutineElement(routine, elementConfig, minSize) => {
            svg.path(
              svg.fill := elementConfig.colorFill.toWebColor.webStyleHexString,
              svg.stroke := elementConfig.colorStroke.toWebColor.webStyleHexString,
              svg.d := dr.renderPath(logger, shape.myBounds).svgPathDString
            )
          }
          case AppShapeTextElement(text, elementConfig) => {
            svg.text(
              svg.fill := elementConfig.colorFill.toWebColor.webStyleHexString,
              svg.stroke := elementConfig.colorStroke.toWebColor.webStyleHexString,
              svg.fontSize := elementConfig.font.sizeInPx + "px",
              svg.fontFamily := elementConfig.font.name,
              text
            )
          }
        }
      }
      case c: AppShapeComposition[Double] => {
        svg.g(
          shape.children.map(renderElementAsSvg(logger, _))
        )
      }
    }
  }

  override def render(logger: Logger, input: AppShapeElement[Double]): ReactiveSvgElement[SVGSVGElement] = {
    logger.logWarn("SvgLaminarRenderer::not correctly implemented yet!")
    val rendered: AppShapeElement.AppElementRendered[Double] = input.renderWithMinimumDimension(AppShapeRenderingConfig.defaultDouble)
    val res = renderSvgImage(logger, rendered)
    println("res: " + res.ref)
    res
  }
}
