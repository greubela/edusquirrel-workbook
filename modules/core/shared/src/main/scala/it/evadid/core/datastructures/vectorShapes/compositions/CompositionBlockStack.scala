package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.*
import it.evadid.core.datastructures.vectorShapes.abstractions.{AlignmentInParent, AppShapeCompositeControl}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.rendering.AppShapeComposition.{AppCompositionDimensioned, AppCompositionMeasured, AppCompositionPositioned, RenderingDimension}

/** Overlays all children in one container according to a shared alignment. */
case class CompositionBlockStack[T: Fractional](alignment: AlignmentInParent) extends AppShapeCompositeControl[T] {
  override def calculateMyMinimumDimension(children: List[AppCompositionMeasured[T]], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
    RenderingDimension.fromRawDimensionAndConfig(minimumRenderingDimension(children), compositionConfig, renderingConfig)
  }

  override def calculateChildrenDimensions(children: List[AppCompositionMeasured[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppCompositionDimensioned[T]] =
    dimensionChildrenAtMinimum(children)

  override def calculateChildrenPositions(children: List[AppCompositionDimensioned[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppCompositionPositioned[T]] = {
    children.map(positionAligned(_, myRenderingSize.rawDimension, alignment))
  }
}
