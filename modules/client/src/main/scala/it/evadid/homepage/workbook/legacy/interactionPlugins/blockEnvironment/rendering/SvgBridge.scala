package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.rendering

import it.evadid.core.datastructures.geometry.{Bounds, Dimension}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.atomarElements.AppPathSvgElement
import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapeAtomic

object SvgBridge {



  private def renderDirectly[T: Fractional](builder: SvgPathBuilder[T]): AppSvgElement = {
    //val controlLines = absoluteCommands.flatMap(_.controlPointsAbsolute) // todo: fix to re-introduce control lines
    AppPathSvgElement[T](builder.toSvgPathD, builder.pathPoints, List())
  }

  def toFixedDimensionShape[T: Fractional](builder: SvgPathBuilder[T]): BeShapeAtomic = new BeShapeAtomic {
    override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = {
      builder.requiresDimension.toDouble
    }

    override def render(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
      //moveWholePath(Dimension(fromDouble(bounds.startPoint.x), fromDouble(bounds.startPoint.y))).
      renderDirectly(builder)
    }
  }


}
