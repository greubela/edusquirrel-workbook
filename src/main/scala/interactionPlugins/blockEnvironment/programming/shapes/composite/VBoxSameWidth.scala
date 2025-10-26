package interactionPlugins.blockEnvironment.programming.shapes.composite

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.compositeElements.{AppDecoratedSvgElement, AppGroupSvgElement}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.*
import interactionPlugins.blockEnvironment.programming.shapes.BeShape

import scala.collection.mutable

case class VBoxSameWidth(override val children: List[BeShape]) extends BeShapeBox {

  def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    val minSizes: List[Dimension[Double]] = children.map(_.displaySize(config))

    val widthMax = minSizes.map(_.width).maxOption.getOrElse(0.0)
    val heightSum = minSizes.map(_.height).sum

    val paddingHeight = if (children.size > 1) config.paddingSmall.height * (children.size - 1) else 0

    Dimension[Double](widthMax, heightSum + paddingHeight)
  }.ensureAtLeastAsBigAs(config.paddingSmall)


  override def calcChildrenBounds(config: BeRenderingConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]] = {
    val minSizes: List[Dimension[Double]] = children.map(_.displaySize(config))

    val widthMax = minSizes.map(_.width).maxOption.getOrElse(0.0)
    val heightSum = minSizes.map(_.height).sum

    val res = mutable.HashMap[BeShape, Bounds[Double]]()
    var curPoint = bounds.startPoint

    for ((curChild, index) <- children.zipWithIndex) {
      val childDim = minSizes(index).ensureWidth(widthMax)
      val childBounds = curPoint.withDimension(childDim)
      curPoint = curPoint.moveWithDimension(Dimension[Double](0, config.paddingSmall.height)).moveWithDimension(Dimension[Double](0, childDim.height))
      res.put(curChild, childBounds)
    }

    res.toMap
  }

}

