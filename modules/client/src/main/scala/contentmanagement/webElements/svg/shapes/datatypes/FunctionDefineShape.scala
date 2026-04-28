package contentmanagement.webElements.svg.shapes.datatypes

import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.ShapeFactory
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import contentmanagement.webElements.svg.shapes.BeShape.BeShapePathBased
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}

object FunctionDefineShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] =
    ???

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] =
    config.paddingSmall.increaseSize(10, 20)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] =
    config.paddingSmall.increaseSize(10, 10)

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = Dimension[Double](40, 25)
}

/*
  override def getAssociatedSvgElement(bounds: Bounds[Double]): AppSvgElement = {
    
  }

  override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
    childDimension.increaseSize(40, 20).increaseSize(config.paddingSmall).increaseSize(config.paddingSmall)
  }

  override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
    val extraWidth = myDimension.width - childDimension.width
    val extraHeight = myDimension.height - childDimension.height
    new Point[Double](extraWidth / 2, extraHeight / 2)
  }

}
*/