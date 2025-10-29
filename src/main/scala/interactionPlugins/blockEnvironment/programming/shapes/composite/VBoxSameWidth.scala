package interactionPlugins.blockEnvironment.programming.shapes.composite

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.*
import interactionPlugins.blockEnvironment.programming.shapes.BeShape

import scala.collection.mutable

case class VBoxSameWidth(
    override val children: List[BeShape],
    usePadding: Boolean = true,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left
) extends BeShapeBox {

  def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    val minSizes: List[Dimension[Double]] = children.map(_.displaySize(config))

    val widthMax = minSizes.map(_.width).maxOption.getOrElse(0.0)
    val heightSum = minSizes.map(_.height).sum

    val paddingHeight = if (children.size > 1 && usePadding) config.paddingSmall.height * (children.size - 1) else 0.0

    Dimension[Double](widthMax, heightSum + paddingHeight)
  }.ensureAtLeastAsBigAs(config.paddingSmall)


  override def calcChildrenBounds(config: BeRenderingConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]] = {
    val minSizes: List[Dimension[Double]] = children.map(_.displaySize(config))

    val widthMax = minSizes.map(_.width).maxOption.getOrElse(0.0)

    val res = mutable.HashMap[BeShape, Bounds[Double]]()
    val availableWidth = bounds.dimension.width - widthMax
    val baseOffset = horizontalAlignment match
      case HorizontalAlignment.Left   => 0.0
      case HorizontalAlignment.Center => availableWidth / 2
      case HorizontalAlignment.Right  => availableWidth
    val safeOffset = math.max(0.0, baseOffset)

    var curPoint = bounds.startPoint

    for ((curChild, index) <- children.zipWithIndex) {
      val childDim = minSizes(index).ensureWidth(widthMax)
      val childStart = Point[Double](bounds.startPoint.x + safeOffset, curPoint.y)
      val childBounds = childStart.withDimension(childDim)
      if (usePadding) {
        curPoint = curPoint.moveWithDimension(Dimension[Double](0, config.paddingSmall.height))
      }
      curPoint = curPoint.moveWithDimension(Dimension[Double](0, childDim.height))
      res.put(curChild, childBounds)
    }

    res.toMap
  }

}

