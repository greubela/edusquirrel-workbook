package contentmanagement.webElements.svg.shapes.datatypes

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.ShapeFactory
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import contentmanagement.webElements.svg.shapes.BeShape.BeShapePathBased


object RectangleShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildRectangle(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall

}

