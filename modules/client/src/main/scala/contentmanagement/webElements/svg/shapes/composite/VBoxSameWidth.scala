package contentmanagement.webElements.svg.shapes.composite

import contentmanagement.webElements.svg.shapes.BeShape
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import BeShape.*
import datastructures.core.geometry.{Bounds, Dimension, Point}

import scala.collection.mutable

case class VBoxSameWidth(
    override val children: List[BeShape],
    usePadding: Boolean = true,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Center,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Center
) extends BeShapeBox {

  def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    val minSizes: List[Dimension[Double]] = children.map(_.displaySize(config))

    val widthMax = minSizes.map(_.width).maxOption.getOrElse(0.0)
    val heightSum = minSizes.map(_.height).sum

    val paddingWidth = if (usePadding) config.paddingSmall.width * 2 else 0.0
    val paddingHeight = if (usePadding) config.paddingSmall.height * (children.size + 1) else 0.0
   // println("heightSum: " + heightSum + " paddingHeight: " + paddingHeight + " (heights: " +  minSizes.map(_.height).mkString(", ") + ")" )
    Dimension[Double](widthMax + paddingWidth, heightSum + paddingHeight)
  }.ensureAtLeastAsBigAs(config.paddingSmall)


  override def calcChildrenBounds(config: BeRenderingConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]] = {
    val minSizes: List[Dimension[Double]] = children.map(_.displaySize(config))

    val widthMax = minSizes.map(_.width).maxOption.getOrElse(0.0)
    val heightSum = minSizes.map(_.height).sum

    val paddingWidth = if (usePadding) config.paddingSmall.width * 2 else 0.0
    val paddingHeight = if (usePadding) config.paddingSmall.height * (children.size + 1) else 0.0

    val availableWidth = bounds.dimension.width - widthMax - paddingWidth
    val horizontalOffset = horizontalAlignment match
      case HorizontalAlignment.Left => 0.0
      case HorizontalAlignment.Center => availableWidth / 2
      case HorizontalAlignment.Right => availableWidth
    val safeHorizontalOffset = math.max(0.0, horizontalOffset)

    val availableHeight = bounds.dimension.height - heightSum - paddingHeight
    val verticalOffset = verticalAlignment match
      case VerticalAlignment.Top => 0.0
      case VerticalAlignment.Center => availableHeight / 2
      case VerticalAlignment.Bottom => availableHeight
    val safeVerticalOffset = math.max(0.0, verticalOffset)


    val startPoint = bounds.startPoint.moveWithDimension(
      Dimension[Double](safeHorizontalOffset, safeVerticalOffset)
    )

    var curY = startPoint.y
    val baseX = startPoint.x + (if (usePadding) config.paddingSmall.width else 0.0)
    if (usePadding) {
      curY += config.paddingSmall.height
    }
    val res = mutable.HashMap[BeShape, Bounds[Double]]()

    for ((curChild, index) <- children.zipWithIndex) {
      val childDim = minSizes(index)
      val extraWidth = math.max(0.0, widthMax - childDim.width)
      val horizontalInset = horizontalAlignment match
        case HorizontalAlignment.Left   => 0.0
        case HorizontalAlignment.Center => extraWidth / 2
        case HorizontalAlignment.Right  => extraWidth
      val childStart = Point[Double](
        baseX + horizontalInset,
        curY
      )
      val childBounds = childStart.withDimension(childDim)

      curY += childDim.height
      if (usePadding) {
        curY += config.paddingSmall.height
      }
      res.put(curChild, childBounds)
    }

    res.toMap


  }

}

