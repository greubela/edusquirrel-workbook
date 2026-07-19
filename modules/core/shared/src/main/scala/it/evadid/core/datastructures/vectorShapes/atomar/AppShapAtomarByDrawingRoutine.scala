package it.evadid.core.datastructures.vectorShapes.atomar

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.{AlignmentInParent, AppShapeAtomar, DrawingRoutine}
import it.evadid.core.datastructures.vectorShapes.config.AppShapeRenderingConfig
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath
import it.evadid.util.logging.Logger

case class AppShapAtomarByDrawingRoutine[T: Fractional](minWidth: Dimension[T], drawingRoutine: DrawingRoutine[T]) extends AppShapeAtomar[T] {

  /**
   * Calculates the minimum size of this AppShapeAtomar as raw value (without paddings or margins)
   */
  override def calculateRawMinimumSize(renderingConfig: AppShapeRenderingConfig[T]): Dimension[T] = minWidth

  override def renderPath(logger: Logger, bounds: Bounds[T], alignIfMisfit: AlignmentInParent): SvgPath =
    drawingRoutine.renderPath(logger, bounds, alignIfMisfit)
}

object AppShapAtomarByDrawingRoutine {


}

