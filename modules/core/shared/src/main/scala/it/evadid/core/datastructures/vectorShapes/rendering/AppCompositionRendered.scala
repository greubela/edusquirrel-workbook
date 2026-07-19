package it.evadid.core.datastructures.vectorShapes.rendering

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeAtomar
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.Offset
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}

case class AppCompositionRendered[T : Fractional](composition: AppShapeComposition[T], shapeConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T], compositionDimension: Dimension[T], compositionOffset: Offset[T]) {

}
