package interactionPlugins.blockEnvironment.programming.shapes.datatypes

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.{AppSvgElement, SvgPathBuilder}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.{BeShapeAtomic, BeShapeContainerable, BeShapePathBased}
import interactionPlugins.blockEnvironment.programming.shapes.ShapeFactory


object UnitShape extends BeShapePathBased {

  def radius: Int = 2

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =
    ShapeFactory.buildUnitShape(bounds, radius)

  private def remainder(nr: Double, intNr: Int) = nr - intNr * (nr/intNr).toInt

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = {
    val remX = remainder(childDim.width, radius)
    val remY = remainder(childDim.height, radius)
    val res = Dimension(2*radius + radius - remX/2, 2*radius+radius - remY/2)
  //  println("childDim: " + childDim + " remX: " + remX + " remY: " + remY + " --> " + res)
    res
  }

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = spaceBeforeChild(config, childDim)

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = Dimension[Double](radius*4, radius*4)
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
