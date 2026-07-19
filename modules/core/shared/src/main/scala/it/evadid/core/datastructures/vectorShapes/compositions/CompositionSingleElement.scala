package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.rendering.AppShapeComposition.RenderingDimension
import it.evadid.core.datastructures.vectorShapes.rendering.{AppShapeComposition, AppShapeConfigured}

case class CompositionSingleElement[T: Fractional](atomar: AppShapeConfigured[T]) extends AppShapeCompositeControl[T] {

  override def calculateMyMinimumDimension(childrenDimensions: List[AppShapeComposition.AppCompositionMeasured[T]], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): AppShapeComposition.RenderingDimension[T] = {
    RenderingDimension.fromRawDimensionAndConfig(atomar.shape.calculateRawMinimumSize(renderingConfig), atomar.shapeConfig, renderingConfig)
  }

  override def calculateChildrenDimensions(children: List[AppShapeComposition.AppCompositionMeasured[T]], myRenderingSize: AppShapeComposition.RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppShapeComposition.AppCompositionDimensioned[T]] = {
    List()
  }

  override def calculateChildrenPositions(children: List[AppShapeComposition.AppCompositionDimensioned[T]], myRenderingSize: AppShapeComposition.RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppShapeComposition.AppCompositionPositioned[T]] = {
    List()
  }
}
