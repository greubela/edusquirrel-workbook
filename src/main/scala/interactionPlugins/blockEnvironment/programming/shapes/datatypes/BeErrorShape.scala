package interactionPlugins.blockEnvironment.programming.shapes.datatypes

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.{AmendedShape, BeShape, ShapeFactory}
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.{BeShapeAtomic, BeShapeContainerable, BeShapePathBased}

case object BeErrorShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildStarterShape(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0, 20)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(0, 20)
}
