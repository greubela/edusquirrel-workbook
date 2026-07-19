package it.evadid.core.datastructures.vectorShapes.rendering

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.{AlignmentInParent, AppShapeAtomar}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.compositions.CompositionSingleElement

case class AppShapeConfigured[T: Fractional](shape: AppShapeAtomar[T], shapeConfig: AppShapeConfig[T]) {

  def toOneElementComposition(alignIfMisfit: Option[AlignmentInParent] = None): AppShapeComposition[T] =
    AppShapeComposition(CompositionSingleElement[T](this, alignIfMisfit), shapeConfig, List())

}
