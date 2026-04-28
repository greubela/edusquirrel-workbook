package contentmanagement.webElements.svg.shapes.composite

import contentmanagement.webElements.svg.shapes.BeShape
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import BeShape.*
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}

import scala.collection.mutable

case class HBoxSameHeight(
    override val children: List[BeShape],
    usePadding: Boolean = true,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Center,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Center
) extends BeShapeBox {

  def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    val minSizes = children.map(_.displaySize(config))

    val widthSum = minSizes.map(_.width).sum
    val heightMax = minSizes.map(_.height).maxOption.getOrElse(0.0)

    val paddingWidth = if (usePadding) config.paddingSmall.width * (children.size + 1) else 0.0
    val paddingHeight = if (usePadding) config.paddingSmall.height * 2 else 0.0

    Dimension[Double](widthSum + paddingWidth, heightMax + paddingHeight)
  }.ensureAtLeastAsBigAs(config.paddingSmall)


  override def calcChildrenBounds(config: BeRenderingConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]] = {
    val minSizes: List[Dimension[Double]] = children.map(_.displaySize(config))

    val widthSum = minSizes.map(_.width).sum
    val heightMax = minSizes.map(_.height).maxOption.getOrElse(0.0)
    
    val paddingWidth = if (usePadding) config.paddingSmall.width * (children.size + 1) else 0.0
    val paddingHeight = if (usePadding) config.paddingSmall.height * 2 else 0.0
    
    val availableWidth = bounds.dimension.width - widthSum - paddingWidth
    val horizontalOffset = horizontalAlignment match
      case HorizontalAlignment.Left   => 0.0
      case HorizontalAlignment.Center => availableWidth / 2
      case HorizontalAlignment.Right  => availableWidth
    val safeHorizontalOffset = math.max(0.0, horizontalOffset)

    val availableHeight = bounds.dimension.height - heightMax - paddingHeight
    val verticalOffset = verticalAlignment match
      case VerticalAlignment.Top    => 0.0
      case VerticalAlignment.Center => availableHeight / 2
      case VerticalAlignment.Bottom => availableHeight
    val safeVerticalOffset = math.max(0.0, verticalOffset)

    val startPoint = bounds.startPoint.moveWithDimension(
      Dimension[Double](safeHorizontalOffset, safeVerticalOffset)
    )

    var curX = startPoint.x
    val baseY = startPoint.y + (if (usePadding) config.paddingSmall.height else 0.0)
    if (usePadding) {
      curX += config.paddingSmall.width
    }
    val res = mutable.HashMap[BeShape, Bounds[Double]]()

    for ((curChild, index) <- children.zipWithIndex) {
      val childDim = minSizes(index)
      val extraHeight = math.max(0.0, heightMax - childDim.height)
      val verticalInset = verticalAlignment match
        case VerticalAlignment.Top    => 0.0
        case VerticalAlignment.Center => extraHeight / 2
        case VerticalAlignment.Bottom => extraHeight
      val childStart = Point[Double](
        curX,
        baseY + verticalInset
      )
      val childBounds = childStart.withDimension(childDim)
      curX += childDim.width
      if (usePadding) {
        curX += config.paddingSmall.width
      }
      res.put(curChild, childBounds)
    }
    
    res.toMap

  }

}
