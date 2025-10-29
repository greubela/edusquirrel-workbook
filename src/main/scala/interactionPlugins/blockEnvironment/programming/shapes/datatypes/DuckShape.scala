package interactionPlugins.blockEnvironment.programming.shapes.datatypes

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.*
import interactionPlugins.blockEnvironment.programming.shapes.ShapeFactory

object DuckShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildDuckShape(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = {
    val minDim = childDim.ensureAtLeastAsBigAs(displaySize(config)).ensureWidth(childDim.height)
    val leftOfText = minDim.height / 25 * 15
    new Dimension[Double](leftOfText, minDim.height).increaseSize(config.paddingSmall).increaseSize(config.paddingSmall)
  }

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = {
    val minDim = childDim.ensureAtLeastAsBigAs(displaySize(config)).ensureWidth(childDim.height)
    val rightOfText = minDim.height / 25 * 10
    new Dimension[Double](rightOfText, 0).increaseSize(config.paddingSmall)
  }

}
/*

  override def displaySize(config: BeRendererConfig): Dimension[Double] = new Dimension[Double](20, 20)

  override def render(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement = {
    
  }

  override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {


    Dimension[Double](minDim.width + leftOfText + rightOfText, minDim.height * 2).increaseSize(config.paddingSmall).increaseSize(config.paddingSmall)
  }

  override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
    val minDim = childDimension.ensureWidth(childDimension.height)
    val leftOfText = minDim.height / 25 * 15
    val rightOfText = minDim.height / 25 * 10

    val availableWidth = myDimension.width - leftOfText - rightOfText
    val extraWidth = availableWidth - childDimension.width

    val availableHeight = myDimension.height / 2
    val extraHeight = availableHeight - childDimension.height

    new Point[Double](leftOfText + extraWidth / 2, myDimension.height / 2 + extraHeight / 2)
  }

}*/