package interactionPlugins.blockEnvironment.programming.shapes.composite

import contentmanagement.model.geometry.*
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.*
import interactionPlugins.blockEnvironment.programming.shapes.BeShape

import scala.collection.mutable

case class HBoxSameHeight(override val children: List[BeShape], usePadding: Boolean = true) extends BeShapeBox {

  def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    val minSizes = children.map(_.displaySize(config))

    val widthSum = minSizes.map(_.width).sum
    val heightMax = minSizes.map(_.height).maxOption.getOrElse(0.0)

    val paddingWidth = if (children.size > 1 && usePadding) config.paddingSmall.width * (children.size - 1) else 0

    Dimension[Double](widthSum + paddingWidth, heightMax)
  }.ensureAtLeastAsBigAs(config.paddingSmall)


  override def calcChildrenBounds(config: BeRenderingConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]] = {
    val minSizes: List[Dimension[Double]] = children.map(_.displaySize(config))

    val widthSum = minSizes.map(_.width).sum
    val heightMax = minSizes.map(_.height).maxOption.getOrElse(0.0)

    val res = mutable.HashMap[BeShape, Bounds[Double]]()
    var curPoint = bounds.startPoint

    for ((curChild, index) <- children.zipWithIndex) {
      val childDim = minSizes(index).ensureHeight(heightMax)
      val childBounds = curPoint.withDimension(childDim)
      if(usePadding){
        curPoint = curPoint.moveWithDimension(Dimension[Double](config.paddingSmall.width, 0))
      }
      curPoint = curPoint.moveWithDimension(Dimension[Double](childDim.width, 0))
      res.put(curChild, childBounds)
    }
    
    res.toMap

  }

}
