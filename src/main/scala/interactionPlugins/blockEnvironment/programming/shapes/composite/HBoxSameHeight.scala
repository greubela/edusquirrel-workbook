package interactionPlugins.blockEnvironment.programming.shapes.composite

import contentmanagement.model.geometry.*
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.*
import interactionPlugins.blockEnvironment.programming.shapes.BeShape

import scala.collection.mutable

case class HBoxSameHeight(
    override val children: List[BeShape],
    usePadding: Boolean = true,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Top
) extends BeShapeBox {

  def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    val minSizes = children.map(_.displaySize(config))

    val widthSum = minSizes.map(_.width).sum
    val heightMax = minSizes.map(_.height).maxOption.getOrElse(0.0)

    val paddingWidth = if (children.size > 1 && usePadding) config.paddingSmall.width * (children.size - 1) else 0.0

    Dimension[Double](widthSum + paddingWidth, heightMax)
  }.ensureAtLeastAsBigAs(config.paddingSmall)


  override def calcChildrenBounds(config: BeRenderingConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]] = {
    val minSizes: List[Dimension[Double]] = children.map(_.displaySize(config))

    val widthSum = minSizes.map(_.width).sum
    val heightMax = minSizes.map(_.height).maxOption.getOrElse(0.0)
    val paddingWidth = if (children.size > 1 && usePadding) config.paddingSmall.width * (children.size - 1) else 0.0

    val res = mutable.HashMap[BeShape, Bounds[Double]]()

    val contentWidth = widthSum + paddingWidth
    val availableWidth = bounds.dimension.width - contentWidth
    val horizontalOffset = horizontalAlignment match
      case HorizontalAlignment.Left   => 0.0
      case HorizontalAlignment.Center => availableWidth / 2
      case HorizontalAlignment.Right  => availableWidth
    val safeHorizontalOffset = math.max(0.0, horizontalOffset)

    val availableHeight = bounds.dimension.height - heightMax
    val verticalOffset = verticalAlignment match
      case VerticalAlignment.Top    => 0.0
      case VerticalAlignment.Center => availableHeight / 2
      case VerticalAlignment.Bottom => availableHeight
    val safeVerticalOffset = math.max(0.0, verticalOffset)

    val startPoint = bounds.startPoint.moveWithDimension(
      Dimension[Double](safeHorizontalOffset, safeVerticalOffset)
    )
    var curPoint = startPoint

    for ((curChild, index) <- children.zipWithIndex) {
      val childDim = minSizes(index).ensureHeight(heightMax)
      val childBounds = curPoint.withDimension(childDim)
      if (usePadding) {
        curPoint = curPoint.moveWithDimension(Dimension[Double](config.paddingSmall.width, 0))
      }
      curPoint = curPoint.moveWithDimension(Dimension[Double](childDim.width, 0))
      res.put(curChild, childBounds)
    }
    
    res.toMap

  }

}
