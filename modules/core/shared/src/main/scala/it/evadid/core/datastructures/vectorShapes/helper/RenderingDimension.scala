package it.evadid.core.datastructures.vectorShapes.helper

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}

case class RenderingDimension[T: Fractional](rawDimension: Dimension[T], fullDimension: Dimension[T]) {
  RenderingDimension.validateDimension(rawDimension, "raw dimensions")
  RenderingDimension.validateDimension(fullDimension, "full dimensions")
  private val N = summon[Fractional[T]]
  require(N.gteq(fullDimension.width, rawDimension.width) && N.gteq(fullDimension.height, rawDimension.height),
    "full dimensions must contain the raw dimensions")
}

object RenderingDimension {
  def validateDimension[T: Fractional](dimension: Dimension[T], label: String): Unit = {
    val N = summon[Fractional[T]]
    val values = List(N.toDouble(dimension.width), N.toDouble(dimension.height))
    require(values.forall(value => value.isFinite && value >= 0), s"$label must be finite and non-negative: $dimension")
  }

  private def padding[T: Fractional](compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): Dimension[T] = {
    val value = compositionConfig.useCustomPadding.getOrElse(renderingConfig.defaultPadding)
    validateDimension(value, "padding")
    value
  }

  def fromRawDimensionAndConfig[T: Fractional](rawDimension: Dimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
    validateDimension(rawDimension, "raw dimensions")
    RenderingDimension(rawDimension, rawDimension.increaseSize(padding(compositionConfig, renderingConfig)))
  }

  def fromFullDimensionAndConfig[T: Fractional](fullDimension: Dimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
    validateDimension(fullDimension, "full dimensions")
    val N = summon[Fractional[T]]
    val reserve = padding(compositionConfig, renderingConfig)
    require(N.gteq(fullDimension.width, reserve.width) && N.gteq(fullDimension.height, reserve.height),
      s"full dimensions must be at least as large as padding: dimensions=$fullDimension, padding=$reserve")
    RenderingDimension(fullDimension.decreaseSize(reserve), fullDimension)
  }
}
