package contentmanagement.webElements.svg.shapes.composite

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.shapes.BeShape
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeBox
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class ShapeStack(
                       children: List[BeShape],
                       horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Center,
                       verticalAlignment: VerticalAlignment = VerticalAlignment.Center,
                       fixedRelativeOffset: Map[BeShape, Point[Double]] = Map()
                     ) extends BeShapeBox {


  override def displaySize(rendererConfig: BeRenderingConfig): Dimension[Double] = {

    val dimRequiredForChild = children.map(curChild => curChild.displaySize(rendererConfig))
    val extraSpaceRequired = children.map(curChild => if (fixedRelativeOffset.contains(curChild)) fixedRelativeOffset(curChild).asDimension else Dimension[Double](0, 0))

    val allDims = dimRequiredForChild.zip(extraSpaceRequired).map(curPair => curPair._1.increaseSize(curPair._2))

    val maxHeight = allDims.map(_.height).max
    val maxWidth = allDims.map(_.width).max

    Dimension(maxWidth, maxHeight)
  }

  override def calcChildrenBounds(rendererConfig: BeRenderingConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]] = {
    children.map(curChild => {
      if (fixedRelativeOffset.contains(curChild)) {

        curChild -> Bounds(bounds.startPoint.moveWithDimension(fixedRelativeOffset(curChild).asDimension), curChild.displaySize(rendererConfig))
      } else {
        val naturalSize = curChild.displaySize(rendererConfig)
        val extraWidth = bounds.width - naturalSize.width
        val extraHeight = bounds.height - naturalSize.height

        val spaceLeft = horizontalAlignment match {
          case HorizontalAlignment.Left => 0
          case HorizontalAlignment.Center => extraWidth / 2
          case HorizontalAlignment.Right => extraWidth
        }
        val spaceTop = verticalAlignment match {
          case VerticalAlignment.Top => 0
          case VerticalAlignment.Center => extraHeight / 2
          case VerticalAlignment.Bottom => extraHeight
        }

        curChild -> bounds.startPoint.moveWithDimension(Dimension(spaceLeft, spaceTop)).withDimension(naturalSize)
      }
    }).toMap


  }
}

object ShapeStack {
  
  
}
