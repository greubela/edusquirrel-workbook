package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.geometry.AspectRatio
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.*
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.{AppElementDimensioned, AppElementMeasured, AppElementPositioned}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.helper.{AlignmentInParent, RenderingDimension}

/** Overlays all children in one container according to a shared alignment. */
case class CompositionBlockStack[T: Fractional](alignment: AlignmentInParent) extends AppShapeCompositeControl[T] {
  override def calculateMyMinimumDimension(children: List[AppElementMeasured[T]], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
    RenderingDimension.fromRawDimensionAndConfig(minimumRenderingDimension(children), compositionConfig, renderingConfig)
  }

  override def calculateChildrenDimensions(children: List[AppElementMeasured[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppElementDimensioned[T]] =
    dimensionChildrenAtMinimum(children)

  override def calculateChildrenPositions(children: List[AppElementDimensioned[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppElementPositioned[T]] = {
    children.map(curChild => positionAligned(curChild, myRenderingSize.rawDimension, alignment))
  }

  override def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)] = None
}
