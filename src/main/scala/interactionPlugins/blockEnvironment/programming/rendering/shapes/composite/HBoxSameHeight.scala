package interactionPlugins.blockEnvironment.programming.rendering.shapes.composite

import contentmanagement.model.geometry.*
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.rendering.BeRendererConfig
import interactionPlugins.blockEnvironment.programming.rendering.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.rendering.shapes.BeShape.*

import scala.collection.mutable

case class HBoxSameHeight(override val children: List[BeShape]) extends BeShapeBox {

  def displaySize(config: BeRendererConfig): Dimension[Double] = {
    val minSizes = children.map(_.displaySize(config))

    val widthSum = minSizes.map(_.width).sum
    val heightMax = minSizes.map(_.height).max

    val paddingWidth = if (children.size > 1) config.paddingSmall.width * (children.size - 1) else 0

    Dimension[Double](widthSum + paddingWidth, heightMax)
  }


  override def calcChildrenBounds(config: BeRendererConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]] = {
    val minSizes: List[Dimension[Double]] = children.map(_.displaySize(config))

    val widthSum = minSizes.map(_.width).sum
    val heightMax = minSizes.map(_.height).max

    val res = mutable.HashMap[BeShape, Bounds[Double]]()
    var curPoint = bounds.startPoint

    for ((curChild, index) <- children.zipWithIndex) {
      val childDim = minSizes(index).ensureHeight(heightMax)
      val childBounds = curPoint.withDimension(childDim)
      curPoint = curPoint.moveWithDimension(Dimension[Double](config.paddingSmall.width, 0)).moveWithDimension(Dimension[Double](childDim.width, 0))
      res.put(curChild, childBounds)
    }
    
    res.toMap

  }

}
