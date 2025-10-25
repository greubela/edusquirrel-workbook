package interactionPlugins.blockEnvironment.programming.rendering.shapes.atomic

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.programming.rendering.BeRendererConfig
import interactionPlugins.blockEnvironment.programming.rendering.shapes.BeShape.{BeShapeAtomic, BeShapeContainerable, BeShapePathBased}
import interactionPlugins.blockEnvironment.programming.rendering.shapes.ShapeFactory

object NumericShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRendererConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =  ShapeFactory.buildNumericShape(bounds)

  override protected def spaceBeforeChild(config: BeRendererConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(5, 0)

  override protected def spaceAfterChild(config: BeRendererConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(5, 0)

}/*extends BeShapeAtomic with BeShapeContainerable {

  override def render(bounds: Bounds[Double]): AppSvgElement = {
   
  }

  override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
    childDimension.increaseSize(config.paddingSmall).increaseSize(config.paddingSmall).increaseSize(10, 0)
  }

  override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
    val extraWidth = myDimension.width - 10 - childDimension.width
    val extraHeight = myDimension.height - childDimension.height
    new Point[Double](5 + extraWidth / 2, extraHeight / 2)
  }

}*/
