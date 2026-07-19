package todomove.webElementsOld.webElements.svg.shapes.datatypes

import todomove.webElementsOld.webElements.svg.shapes.BeShape.{BeShapeAtomic, BeShapeContainerable, BeShapePathBased}
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.AppSvgElement
import todomove.webElementsOld.webElements.svg.shapes.ShapeFactory

object NumericShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =  ShapeFactory.buildNumericShape(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(5, 0)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = config.paddingSmall.increaseSize(5, 0)

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
