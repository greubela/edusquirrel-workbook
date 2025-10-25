package interactionPlugins.blockEnvironment.programming.rendering.shapes.atomic

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.programming.rendering.BeRendererConfig
import interactionPlugins.blockEnvironment.programming.rendering.shapes.BeShape.{BeShapeAtomic, BeShapeContainerable, BeShapePathBased}
import interactionPlugins.blockEnvironment.programming.rendering.shapes.ShapeFactory


object FunctionCallShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRendererConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =
    ShapeFactory.buildUnitShape(bounds)

  override protected def spaceBeforeChild(config: BeRendererConfig, childDim: Dimension[Double]): Dimension[Double] =
    config.paddingSmall.increaseSize(10, 10)

  override protected def spaceAfterChild(config: BeRendererConfig, childDim: Dimension[Double]): Dimension[Double] =
    config.paddingSmall.increaseSize(10, 10)

}
/*

  override def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
    
  }

  override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
    childDimension.increaseSize(40, 20).increaseSize(config.paddingSmall).increaseSize(config.paddingSmall)
  }

  override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
    val extraWidth = myDimension.width - 40 - childDimension.width
    val extraHeight = myDimension.height - 20 - childDimension.height
    new Point[Double](extraWidth / 2, extraHeight / 2)
  }
}*/
