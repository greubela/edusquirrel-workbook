package it.evadid.core.datastructures.vectorShapes.renderer


import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.{AppElementRendered, AppShapeAtomar, AppShapeComposition}
import it.evadid.core.datastructures.vectorShapes.atomar.{AppShapeDrawingRoutineElement, AppShapeTextElement}
import it.evadid.core.datastructures.vectorShapes.config.AppShapeRenderingConfig
import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.svg.{SvgPathBuilderImmutable, TurtlePathBuilder}
import it.evadid.util.logging.Logger
import org.scalajs.dom.SVGSVGElement

object SvgLaminarRenderer extends SvgRenderer[Double, ReactiveSvgElement[SVGSVGElement]] {

  /** Interactive rendering used for the authored/expected turtle drawing. */
  def renderExpectedTurtlePath(built: TurtlePathBuilder[Double]): ReactiveSvgElement[SVGSVGElement] = {
    val pad = 16.0
    val points = built.svgPathBuilder.pathPoints
    val minX = points.map(_.x).minOption.getOrElse(0.0)
    val maxX = points.map(_.x).maxOption.getOrElse(0.0)
    val minY = points.map(_.y).minOption.getOrElse(0.0)
    val maxY = points.map(_.y).maxOption.getOrElse(0.0)
    val width = math.max(50.0, maxX - minX + pad * 2)
    val height = math.max(50.0, maxY - minY + pad * 2)
    val delta = Dimension(pad - minX, pad - minY)

    val expectedLines = built.completedStyledSegments.map { segment =>
      val shifted = segment.pathBuilder.moveWholePath(delta).asInstanceOf[SvgPathBuilderImmutable[Double]]
      svg.path(
        svg.cls := "turtle-expected-line",
        svg.fill := "none",
        svg.stroke := segment.style.color,
        svg.strokeWidth := math.max(2.0, segment.style.size).toString,
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round",
        svg.d := shifted.toSvgPathD
      )
    }
    val turnMarkers = VmToSvg.turnArcs(built).map { arc =>
      val shifted = arc.path.moveWholePath(delta).asInstanceOf[SvgPathBuilderImmutable[Double]]
      val label = s"${formatDegrees(arc.degrees)}°"
      svg.path(
        svg.cls := "turtle-expected-turn-arc",
        svg.fill := "none",
        svg.d := shifted.toSvgPathD,
        svg.titleTag(label)
      )
    }

    svg.svg(
      svg.cls := "turtle-expected-preview",
      svg.width := width.toString,
      svg.height := height.toString,
      svg.viewBox := s"0 0 $width $height",
      expectedLines,
      turnMarkers
    )
  }

  private def formatDegrees(value: Double): String =
    if value == value.round.toDouble then value.round.toString else f"$value%.1f"

  def renderSvgImage(logger: Logger, shape: AppElementRendered[Double]): ReactiveSvgElement[SVGSVGElement] = {
    svg.svg(
      svg.width := s"${shape.myBounds.dimension.width}",
      svg.height := s"${shape.myBounds.dimension.height}",
      svg.viewBox := s"${shape.myBounds.startPoint.x} ${shape.myBounds.startPoint.y} ${shape.myBounds.dimension.width} ${shape.myBounds.dimension.height}",
      svg.fill := (if shape.elementConfig.fillEnabled then shape.elementConfig.colorFill.toWebColor.webStyleHexString else "none"),
      svg.stroke := shape.elementConfig.colorStroke.toWebColor.webStyleHexString,
      svg.strokeWidth := shape.elementConfig.strokeWidth.toString,
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
              svg.x := "0",
              svg.y := "0",
              svg.fill := (if elementConfig.fillEnabled then elementConfig.colorFill.toWebColor.webStyleHexString else "none"),
              svg.stroke := elementConfig.colorStroke.toWebColor.webStyleHexString,
              svg.strokeWidth := elementConfig.strokeWidth.toString,
              svg.d := dr.renderPath(logger, shape.myBounds).svgPathDString
            )
          }
          case AppShapeTextElement(text, elementConfig) => {
            svg.text(
              svg.x := "" + shape.myBounds.startPoint.x,
              svg.y := "" + shape.myBounds.startPoint.y,
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
