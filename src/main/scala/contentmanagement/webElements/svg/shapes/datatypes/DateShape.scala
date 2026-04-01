package contentmanagement.webElements.svg.shapes.datatypes

import contentmanagement.webElements.svg.shapes.ShapeFactory
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import contentmanagement.webElements.svg.shapes.BeShape.{BeShapeAtomic, BeShapeContainerable, BeShapePathBased}
import datastructures.core.geometry.{Bounds, Dimension, Point}

object DateShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildDateShape[Double](bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(5, 0)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(5, 0)

}