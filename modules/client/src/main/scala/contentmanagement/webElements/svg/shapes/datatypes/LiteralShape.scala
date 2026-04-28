package contentmanagement.webElements.svg.shapes.datatypes

import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.shapes.BeShape.{BeShapeAtomic, BeShapeContainerable, BeShapePathBased}

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import contentmanagement.webElements.svg.shapes.ShapeFactory
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import it.evadid.core.datastructures.color.RGBColor
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}

object LiteralShape extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildLiteralShape(bounds)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] =
    config.paddingSmall.increaseSize(childDim.height / 2, 0).increaseSize(childDim.height / 5, 0)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] =
    config.paddingSmall.increaseSize(childDim.height / 10, 0)

  

}/*extends BeShapeAtomic with BeShapeContainerable {
  override def render(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement =

  override def minSizeToContainChild(config: BeRendererConfig, childDimension: Dimension[Double]): Dimension[Double] = {
    val minDim = childDimension.ensureWidth(childDimension.height)
    Dimension[Double](minDim.width + minDim.height / 2 + minDim.height / 10, minDim.height).increaseSize(config.paddingSmall).increaseSize(config.paddingSmall)
  }

  override def getRelativeChildOffset(config: BeRendererConfig, childDimension: Dimension[Double], myDimension: Dimension[Double]): Point[Double] = {
    val availableWidth = myDimension.width - myDimension.height / 2 - myDimension.height / 10
    val extraWidth = availableWidth - childDimension.width

    val availableHeight = myDimension.height
    val extraHeight = availableHeight - childDimension.height

    new Point[Double](myDimension.height / 2 + extraWidth / 2, extraHeight / 2)
  }

}*/