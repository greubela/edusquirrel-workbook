package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.geometry.AspectRatio
import it.evadid.core.datastructures.vectorShapes.abstractions.AlignmentInParent.DistortionAlignment
import it.evadid.core.datastructures.vectorShapes.abstractions.{AlignmentInParent, AppShapeCompositeControl}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.rendering.AppShapeComposition.RenderingDimension
import it.evadid.core.datastructures.vectorShapes.rendering.{AppShapeComposition, AppShapeConfigured}

/** Leaf composition control that obtains its minimum size from one configured atomic shape. */
case class CompositionSingleElement[T: Fractional](atomar: AppShapeConfigured[T], alignmentInParent: Option[AlignmentInParent]) extends AppShapeCompositeControl[T] {

  override def calculateMyMinimumDimension(childrenDimensions: List[AppShapeComposition.AppCompositionMeasured[T]], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): AppShapeComposition.RenderingDimension[T] = {
    RenderingDimension.fromRawDimensionAndConfig(atomar.shape.calculateRawMinimumSize(renderingConfig), atomar.shapeConfig, renderingConfig)
  }

  override def calculateChildrenDimensions(children: List[AppShapeComposition.AppCompositionMeasured[T]], myRenderingSize: AppShapeComposition.RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppShapeComposition.AppCompositionDimensioned[T]] = {
    List()
  }

  override def calculateChildrenPositions(children: List[AppShapeComposition.AppCompositionDimensioned[T]], myRenderingSize: AppShapeComposition.RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppShapeComposition.AppCompositionPositioned[T]] = {
    List()
  }

  override def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)] = atomar.shape.hasDesiredAspectRatio.map((_, alignmentInParent.getOrElse(DistortionAlignment)))
}
