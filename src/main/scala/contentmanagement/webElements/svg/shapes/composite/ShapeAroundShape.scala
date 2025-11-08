package contentmanagement.webElements.svg.shapes.composite

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import contentmanagement.webElements.svg.shapes.BeShape
import contentmanagement.webElements.svg.shapes.BeShape.{BeShapeComposite, BeShapeContainerable}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig


case class ShapeAroundShape(outerShape: BeShapeContainerable, innerShape: BeShape) extends BeShapeComposite {

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    val innerDim = innerShape.displaySize(config)
    outerShape.minSizeToContainChild(config, innerDim)
  }

  lazy val children: List[BeShape] = List(outerShape.asInstanceOf[BeShape], innerShape)

  def render(config: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val innerDim = innerShape.displaySize(config)
    val outerDim = outerShape.minSizeToContainChild(config, innerDim)

    val outerBounds = bounds.startPoint.withDimension(outerDim)

    println("offset: " + outerShape.getRelativeChildOffset(config, innerDim, outerDim))
    val offset = outerShape.getRelativeChildOffset(config, innerDim, outerDim)
    val innerBounds = outerBounds.startPoint.moveWithDimension(offset.asDimension).withDimension(innerDim)

    //val childBounds = outerShape.getChildBounds(config, bounds, innerShape.displaySize(config))
    val inner = innerShape.render(config, innerBounds)
    val outer = outerShape.render(config, outerBounds)
    AppDecoratedSvgElement(outer, List(inner), List())
  }

}
