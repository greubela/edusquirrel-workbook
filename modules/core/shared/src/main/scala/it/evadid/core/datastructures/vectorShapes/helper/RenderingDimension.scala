package it.evadid.core.datastructures.vectorShapes.helper

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}

case class RenderingDimension[T: Fractional](rawDimension: Dimension[T], fullDimension: Dimension[T])

object RenderingDimension {
  def fromRawDimensionAndConfig[T: Fractional](rawDimension: Dimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
    val padding: Dimension[T] = compositionConfig.useCustomPadding.getOrElse(renderingConfig.defaultPadding)
    // val margin = shapeConfig.useCustomMargin.getOrElse(renderingConfig.defaultMargin)
    val full = rawDimension.increaseSize(padding) //.increaseSize(margin)
    RenderingDimension(rawDimension, full)
  }

  def fromFullDimensionAndConfig[T: Fractional](fullDimension: Dimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
    val padding: Dimension[T] = compositionConfig.useCustomPadding.getOrElse(renderingConfig.defaultPadding)
    RenderingDimension(fullDimension.decreaseSize(padding), fullDimension)
  }
}