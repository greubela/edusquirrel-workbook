package it.evadid.core.datastructures.vectorShapes.rendering

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeAtomar
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}

case class AppShapeConfigured[T: Fractional](shape: AppShapeAtomar[T], shapeConfig: AppShapeConfig[T]) {

  def toOneElementComposition: AppShapeComposition[T] = ???

}
