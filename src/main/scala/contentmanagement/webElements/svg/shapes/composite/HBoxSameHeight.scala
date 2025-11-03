package contentmanagement.webElements.svg.shapes.composite

import contentmanagement.model.geometry.*
import contentmanagement.webElements.svg.shapes.BeShape
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import BeShape.*

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

    val paddingWidth = if (children.size > 1 && usePadding) config.paddingSmall.width * (children.size - 1) else 0.0

    Dimension[Double](widthSum + paddingWidth, heightMax)
  }.ensureAtLeastAsBigAs(config.paddingSmall)


  override def calcChildrenBounds(config: BeRenderingConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]] = {
    val minSizes: List[Dimension[Double]] = children.map(_.displaySize(config))

    val widthSum = minSizes.map(_.width).sum
    val heightMax = minSizes.map(_.height).maxOption.getOrElse(0.0)
    
    val paddingWidth = if (children.size > 1 && usePadding) config.paddingSmall.width * (children.size + 1) else 0.0
    val paddingHeight = if(usePadding) config.paddingSmall.height * 2 else 0.0
    

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
    
    var curPoint = startPoint
    val res = mutable.HashMap[BeShape, Bounds[Double]]()

    for ((curChild, index) <- children.zipWithIndex) {
      if (usePadding) {
        curPoint = curPoint.moveWithDimension(Dimension[Double](config.paddingSmall.width, 0))
      }
      val childDim = minSizes(index).ensureHeight(heightMax)
      val childBounds = curPoint.withDimension(childDim)
    
      curPoint = curPoint.moveWithDimension(Dimension[Double](childDim.width, 0))
      res.put(curChild, childBounds)
    }
    
    res.toMap

  }

}
