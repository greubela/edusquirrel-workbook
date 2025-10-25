package interactionPlugins.blockEnvironment.programming.rendering.shapes.atomic

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.programming.rendering.BeRendererConfig
import interactionPlugins.blockEnvironment.programming.rendering.shapes.BeShape.{BeShapeAtomic, BeShapeContainerable, BeShapePathBased}
import interactionPlugins.blockEnvironment.programming.rendering.shapes.ShapeFactory


 object BooleanShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRendererConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildBooleanShape(bounds)

  override protected def spaceBeforeChild(config: BeRendererConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(5, 0)

  override protected def spaceAfterChild(config: BeRendererConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(5, 0)

}