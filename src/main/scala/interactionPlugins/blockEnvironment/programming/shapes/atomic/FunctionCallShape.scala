package interactionPlugins.blockEnvironment.programming.shapes.atomic

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.{BeShapeAtomic, BeShapeContainerable, BeShapePathBased}
import interactionPlugins.blockEnvironment.programming.shapes.ShapeFactory


object FunctionCallShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =
    ShapeFactory.buildUnitShape(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] =
    config.paddingSmall.increaseSize(10, 10)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] =
    config.paddingSmall.increaseSize(10, 10)

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = Dimension[Double](40, 2)
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
