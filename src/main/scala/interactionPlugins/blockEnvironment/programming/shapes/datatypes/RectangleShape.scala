package interactionPlugins.blockEnvironment.programming.shapes.datatypes

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.SvgPathBuilder
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.BeShapePathBased
import interactionPlugins.blockEnvironment.programming.shapes.ShapeFactory


object RectangleShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildRectangle(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall

}

