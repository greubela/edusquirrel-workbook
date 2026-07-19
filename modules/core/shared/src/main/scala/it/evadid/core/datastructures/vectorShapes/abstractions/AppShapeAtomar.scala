package it.evadid.core.datastructures.vectorShapes.abstractions

import it.evadid.core.datastructures.geometry.{AspectRatio, Bounds, Dimension}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.rendering.AppShapeConfigured
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath
import it.evadid.util.logging.Logger

trait AppShapeAtomar[T: Fractional] {

  /**
   * Calculates the minimum size of this AppShapeAtomar as raw value (without paddings or margins)
   */
  def calculateRawMinimumSize(renderingConfig: AppShapeRenderingConfig[T]): Dimension[T]

  def withConfig(config: AppShapeConfig[T]): AppShapeConfigured[T] = AppShapeConfigured[T](this, config)

  def renderPath(logger: Logger, bounds: Bounds[T]): SvgPath

  def hasDesiredAspectRatio: Option[AspectRatio]

}


